/*
 * Copyright (c) 2025 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package ch.so.agi.hop.geoprocessing.vendor.jts.coverage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.dissolve.LineDissolver;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.noding.NodedSegmentString;
import org.locationtech.jts.noding.Noder;
import org.locationtech.jts.noding.SegmentStringUtil;
import org.locationtech.jts.noding.snap.SnappingNoder;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.util.IntArrayList;

public class CoverageCleaner {
  public static final int MERGE_LONGEST_BORDER = 0;
  public static final int MERGE_MAX_AREA = 1;
  public static final int MERGE_MIN_AREA = 2;
  public static final int MERGE_MIN_INDEX = 3;

  private static final double DEFAULT_SNAPPING_FACTOR = 1.0e8;

  private final Geometry[] coverage;
  private final GeometryFactory geometryFactory;
  private final HashMap<Integer, IntArrayList> overlapParentMap = new HashMap<>();
  private final List<Polygon> overlaps = new ArrayList<>();
  private final List<Polygon> gaps = new ArrayList<>();

  private double snappingDistance;
  private double gapMaximumWidth;
  private int overlapMergeStrategy = MERGE_LONGEST_BORDER;
  private STRtree coverageIndex;
  private Polygon[] resultants;
  private CleanCoverage cleanCoverage;
  private List<Polygon> mergeableGaps;

  public static Geometry[] clean(
      Geometry[] coverage, double snappingDistance, int overlapMergeStrategy, double maxGapWidth) {
    CoverageCleaner cleaner = new CoverageCleaner(coverage);
    cleaner.setSnappingDistance(snappingDistance);
    cleaner.setGapMaximumWidth(maxGapWidth);
    cleaner.setOverlapMergeStrategy(overlapMergeStrategy);
    cleaner.clean();
    return cleaner.getResult();
  }

  public static Geometry[] clean(Geometry[] coverage, double snappingDistance, double maxGapWidth) {
    CoverageCleaner cleaner = new CoverageCleaner(coverage);
    cleaner.setSnappingDistance(snappingDistance);
    cleaner.setGapMaximumWidth(maxGapWidth);
    cleaner.clean();
    return cleaner.getResult();
  }

  public static Geometry[] cleanOverlapGap(
      Geometry[] coverage, int overlapMergeStrategy, double maxGapWidth) {
    return clean(coverage, -1.0d, overlapMergeStrategy, maxGapWidth);
  }

  public static Geometry[] cleanGapWidth(Geometry[] coverage, double maxGapWidth) {
    return clean(coverage, -1.0d, maxGapWidth);
  }

  public CoverageCleaner(Geometry[] coverage) {
    this.coverage = coverage;
    this.geometryFactory = coverage[0].getFactory();
    this.snappingDistance = computeDefaultSnappingDistance(coverage);
  }

  public void setSnappingDistance(double snappingDistance) {
    if (snappingDistance >= 0.0d) {
      this.snappingDistance = snappingDistance;
    }
  }

  public void setOverlapMergeStrategy(int mergeStrategy) {
    if (mergeStrategy < MERGE_LONGEST_BORDER || mergeStrategy > MERGE_MIN_INDEX) {
      throw new IllegalArgumentException("Invalid merge strategy code: " + mergeStrategy);
    }
    this.overlapMergeStrategy = mergeStrategy;
  }

  public void setGapMaximumWidth(double maxWidth) {
    if (maxWidth >= 0.0d) {
      this.gapMaximumWidth = maxWidth;
    }
  }

  public void clean() {
    computeResultants(snappingDistance);
    mergeOverlaps(overlapParentMap);
    cleanCoverage.mergeGaps(mergeableGaps);
  }

  public Geometry[] getResult() {
    return cleanCoverage.toCoverage(geometryFactory);
  }

  public List<Polygon> getOverlaps() {
    return overlaps;
  }

  public List<Polygon> getMergedGaps() {
    return mergeableGaps;
  }

  private static double computeDefaultSnappingDistance(Geometry[] geometries) {
    return extent(geometries).getDiameter() / DEFAULT_SNAPPING_FACTOR;
  }

  private static Envelope extent(Geometry[] geometries) {
    Envelope envelope = new Envelope();
    for (Geometry geometry : geometries) {
      envelope.expandToInclude(geometry.getEnvelopeInternal());
    }
    return envelope;
  }

  private void mergeOverlaps(HashMap<Integer, IntArrayList> overlapParentMap) {
    for (int resultIndex : overlapParentMap.keySet()) {
      cleanCoverage.mergeOverlap(
          resultants[resultIndex], mergeStrategy(overlapMergeStrategy), overlapParentMap.get(resultIndex));
    }
  }

  private CleanCoverage.MergeStrategy mergeStrategy(int mergeStrategyId) {
    return switch (mergeStrategyId) {
      case MERGE_LONGEST_BORDER -> new CleanCoverage.MergeStrategy.BorderMergeStrategy();
      case MERGE_MAX_AREA -> new CleanCoverage.MergeStrategy.AreaMergeStrategy(true);
      case MERGE_MIN_AREA -> new CleanCoverage.MergeStrategy.AreaMergeStrategy(false);
      case MERGE_MIN_INDEX -> new CleanCoverage.MergeStrategy.IndexMergeStrategy(false);
      default -> throw new IllegalArgumentException("Unknown merge strategy: " + mergeStrategyId);
    };
  }

  private void computeResultants(double tolerance) {
    Geometry nodedEdges = node(coverage, tolerance);
    Geometry cleanEdges = LineDissolver.dissolve(nodedEdges);
    resultants = polygonize(cleanEdges);
    cleanCoverage = new CleanCoverage(coverage.length);
    createCoverageIndex();
    classifyResults(resultants);
    mergeableGaps = findMergeableGaps(gaps);
  }

  private void createCoverageIndex() {
    coverageIndex = new STRtree();
    for (int index = 0; index < coverage.length; index++) {
      coverageIndex.insert(coverage[index].getEnvelopeInternal(), index);
    }
  }

  private void classifyResults(Polygon[] resultants) {
    for (int index = 0; index < resultants.length; index++) {
      classifyResultant(index, resultants[index]);
    }
  }

  private void classifyResultant(int resultIndex, Polygon resultPolygon) {
    Point interiorPoint = resultPolygon.getInteriorPoint();
    int parentIndex = -1;
    IntArrayList overlapIndexes = null;

    @SuppressWarnings("unchecked")
    List<Integer> candidateParentIndexes = coverageIndex.query(interiorPoint.getEnvelopeInternal());
    for (int candidateIndex : candidateParentIndexes) {
      Geometry parent = coverage[candidateIndex];
      if (covers(parent, interiorPoint)) {
        if (parentIndex < 0) {
          parentIndex = candidateIndex;
        } else {
          if (overlapIndexes == null) {
            overlapIndexes = new IntArrayList();
          }
          overlapIndexes.add(parentIndex);
          overlapIndexes.add(candidateIndex);
        }
      }
    }

    if (parentIndex < 0) {
      gaps.add(resultPolygon);
    } else if (overlapIndexes != null) {
      overlapParentMap.put(resultIndex, overlapIndexes);
      overlaps.add(resultPolygon);
    } else {
      cleanCoverage.add(parentIndex, resultPolygon);
    }
  }

  private static boolean covers(Geometry polygon, Point point) {
    return SimplePointInAreaLocator.isContained(point.getCoordinate(), polygon);
  }

  private List<Polygon> findMergeableGaps(List<Polygon> ignored) {
    return gaps.stream().filter(this::isMergeableGap).collect(Collectors.toList());
  }

  private boolean isMergeableGap(Polygon gap) {
    if (gapMaximumWidth <= 0.0d) {
      return false;
    }
    return isRadiusWithin(gap, gapMaximumWidth / 2.0d);
  }

  // JTS 1.20.0 does not expose MaximumInscribedCircle.isRadiusWithin yet.
  private boolean isRadiusWithin(Geometry polygonal, double maxRadius) {
    if (maxRadius <= 0.0d) {
      return false;
    }
    double tolerance = Math.max(maxRadius / 100.0d, 1.0e-12d);
    return MaximumInscribedCircle.getRadiusLine(polygonal, tolerance).getLength() <= maxRadius;
  }

  private static Polygon[] polygonize(Geometry cleanEdges) {
    Polygonizer polygonizer = new Polygonizer();
    polygonizer.add(cleanEdges);
    return toPolygonArray(polygonizer.getGeometry());
  }

  public static Geometry node(Geometry[] coverage, double snapDistance) {
    List<NodedSegmentString> segments = new ArrayList<>();
    for (Geometry geometry : coverage) {
      if (!isPolygonal(geometry) || geometry.isEmpty()) {
        continue;
      }
      extractNodedSegmentStrings(geometry, segments);
    }
    Noder noder = new SnappingNoder(snapDistance);
    noder.computeNodes(segments);
    @SuppressWarnings("rawtypes")
    Collection nodedSegments = noder.getNodedSubstrings();
    return SegmentStringUtil.toGeometry(nodedSegments, coverage[0].getFactory());
  }

  private static boolean isPolygonal(Geometry geometry) {
    return geometry instanceof Polygon || geometry instanceof MultiPolygon;
  }

  private static void extractNodedSegmentStrings(
      Geometry geometry, List<NodedSegmentString> segments) {
    @SuppressWarnings("unchecked")
    List<NodedSegmentString> geometrySegments = SegmentStringUtil.extractNodedSegmentStrings(geometry);
    segments.addAll(geometrySegments);
  }

  private static Polygon[] toPolygonArray(Geometry geometry) {
    Polygon[] polygons = new Polygon[geometry.getNumGeometries()];
    for (int index = 0; index < geometry.getNumGeometries(); index++) {
      polygons[index] = (Polygon) geometry.getGeometryN(index);
    }
    return polygons;
  }
}
