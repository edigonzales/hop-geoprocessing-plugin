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
import java.util.Arrays;
import java.util.List;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.relateng.IntersectionMatrixPattern;
import org.locationtech.jts.operation.relateng.RelateNG;
import org.locationtech.jts.util.IntArrayList;

class CleanCoverage {

  private final CleanArea[] coverageAreas;
  private Quadtree coverageIndex;

  CleanCoverage(int size) {
    this.coverageAreas = new CleanArea[size];
  }

  void add(int index, Polygon polygon) {
    if (coverageAreas[index] == null) {
      coverageAreas[index] = new CleanArea();
    }
    coverageAreas[index].add(polygon);
  }

  void mergeOverlap(Polygon overlap, MergeStrategy mergeStrategy, IntArrayList parentIndexes) {
    int mergeTarget = findMergeTarget(overlap, mergeStrategy, parentIndexes, coverageAreas);
    add(mergeTarget, overlap);
  }

  static int findMergeTarget(
      Polygon polygon, MergeStrategy strategy, IntArrayList parentIndexes, CleanArea[] coverageAreas) {
    int[] indexesAsc = parentIndexes.toArray();
    Arrays.sort(indexesAsc);
    for (int areaIndex : indexesAsc) {
      strategy.checkMergeTarget(areaIndex, coverageAreas[areaIndex], polygon);
    }
    return strategy.getTarget();
  }

  void mergeGaps(List<Polygon> gaps) {
    createIndex();
    for (Polygon gap : gaps) {
      mergeGap(gap);
    }
  }

  Geometry[] toCoverage(GeometryFactory geometryFactory) {
    Geometry[] cleaned = new Geometry[coverageAreas.length];
    for (int index = 0; index < coverageAreas.length; index++) {
      cleaned[index] =
          coverageAreas[index] == null ? geometryFactory.createEmpty(2) : coverageAreas[index].union();
    }
    return cleaned;
  }

  private void mergeGap(Polygon gap) {
    List<CleanArea> adjacentAreas = findAdjacentAreas(gap);
    if (adjacentAreas.isEmpty()) {
      return;
    }

    CleanArea mergeTarget = findMaxBorderLength(gap, adjacentAreas);
    coverageIndex.remove(mergeTarget.getEnvelope(), mergeTarget);
    mergeTarget.add(gap);
    coverageIndex.insert(mergeTarget.getEnvelope(), mergeTarget);
  }

  private CleanArea findMaxBorderLength(Polygon polygon, List<CleanArea> areas) {
    double maxLength = 0.0d;
    CleanArea maxLengthArea = null;
    for (CleanArea area : areas) {
      double borderLength = area.getBorderLength(polygon);
      if (maxLengthArea == null || borderLength > maxLength) {
        maxLength = borderLength;
        maxLengthArea = area;
      }
    }
    return maxLengthArea;
  }

  private List<CleanArea> findAdjacentAreas(Geometry polygon) {
    List<CleanArea> adjacentAreas = new ArrayList<>();
    RelateNG relate = RelateNG.prepare(polygon);
    @SuppressWarnings("unchecked")
    List<CleanArea> candidates = coverageIndex.query(polygon.getEnvelopeInternal());
    for (CleanArea area : candidates) {
      if (area != null && area.isAdjacent(relate)) {
        adjacentAreas.add(area);
      }
    }
    return adjacentAreas;
  }

  private void createIndex() {
    coverageIndex = new Quadtree();
    for (CleanArea area : coverageAreas) {
      if (area != null) {
        coverageIndex.insert(area.getEnvelope(), area);
      }
    }
  }

  private static class CleanArea {
    private final List<Polygon> polygons = new ArrayList<>();

    void add(Polygon polygon) {
      polygons.add(polygon);
    }

    Envelope getEnvelope() {
      Envelope envelope = new Envelope();
      for (Polygon polygon : polygons) {
        envelope.expandToInclude(polygon.getEnvelopeInternal());
      }
      return envelope;
    }

    double getBorderLength(Polygon adjacentPolygon) {
      double length = 0.0d;
      for (Polygon polygon : polygons) {
        Geometry border = OverlayNGRobust.overlay(polygon, adjacentPolygon, OverlayNG.INTERSECTION);
        length += border.getLength();
      }
      return length;
    }

    double getArea() {
      double area = 0.0d;
      for (Polygon polygon : polygons) {
        area += polygon.getArea();
      }
      return area;
    }

    boolean isAdjacent(RelateNG relate) {
      for (Polygon polygon : polygons) {
        if (relate.evaluate(polygon, IntersectionMatrixPattern.ADJACENT)) {
          return true;
        }
      }
      return false;
    }

    Geometry union() {
      return CoverageUnion.union(GeometryFactory.toGeometryArray(polygons));
    }
  }

  interface MergeStrategy {

    int getTarget();

    void checkMergeTarget(int areaIndex, CleanArea cleanArea, Polygon polygon);

    final class BorderMergeStrategy implements MergeStrategy {
      private int targetIndex = -1;
      private double targetBorderLength;

      @Override
      public int getTarget() {
        return targetIndex;
      }

      @Override
      public void checkMergeTarget(int areaIndex, CleanArea cleanArea, Polygon polygon) {
        double borderLength = cleanArea == null ? 0.0d : cleanArea.getBorderLength(polygon);
        if (targetIndex < 0 || borderLength > targetBorderLength) {
          targetIndex = areaIndex;
          targetBorderLength = borderLength;
        }
      }
    }

    final class AreaMergeStrategy implements MergeStrategy {
      private final boolean maxArea;
      private int targetIndex = -1;
      private double targetArea;

      AreaMergeStrategy(boolean maxArea) {
        this.maxArea = maxArea;
      }

      @Override
      public int getTarget() {
        return targetIndex;
      }

      @Override
      public void checkMergeTarget(int areaIndex, CleanArea cleanArea, Polygon polygon) {
        double area = cleanArea == null ? 0.0d : cleanArea.getArea();
        boolean isBetter = maxArea ? area > targetArea : area < targetArea;
        if (targetIndex < 0 || isBetter) {
          targetIndex = areaIndex;
          targetArea = area;
        }
      }
    }

    final class IndexMergeStrategy implements MergeStrategy {
      private final boolean maxIndex;
      private int targetIndex = -1;

      IndexMergeStrategy(boolean maxIndex) {
        this.maxIndex = maxIndex;
      }

      @Override
      public int getTarget() {
        return targetIndex;
      }

      @Override
      public void checkMergeTarget(int areaIndex, CleanArea cleanArea, Polygon polygon) {
        boolean isBetter = maxIndex ? areaIndex > targetIndex : areaIndex < targetIndex;
        if (targetIndex < 0 || isBetter) {
          targetIndex = areaIndex;
        }
      }
    }
  }
}
