package ch.so.agi.hop.geoprocessing.core.reproject;

import com.atolcd.hop.gis.geometry.codec.GeometryCodecException;
import com.atolcd.hop.gis.geometry.codec.GeometrySridSupport;
import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.NoSuchAuthorityCodeException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.crs.GeographicCRS;
import org.geotools.api.referencing.crs.ProjectedCRS;
import org.geotools.api.referencing.operation.CoordinateOperation;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.OperationNotFoundException;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;

/** Strict horizontal EPSG reprojection using the separately installed shared Geometry runtime. */
public final class GeoToolsReprojectionService {
  static final int MAX_OPERATIONS = 64;
  private final Consumer<String> operationLogger;
  private final Map<OperationKey, MathTransform> operations =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<OperationKey, MathTransform> eldest) {
          return size() > MAX_OPERATIONS;
        }
      };

  public GeoToolsReprojectionService(Consumer<String> operationLogger) {
    this.operationLogger = operationLogger == null ? message -> {} : operationLogger;
  }

  /** Validates active fixed CRS settings before processing any rows. */
  public void validateConfiguration(GeometryReprojectSettings settings)
      throws ReprojectConfigurationException, ReprojectIntegrationException {
    GeometryReprojectSchema.validateSettings(settings);
    int target = GeometryReprojectSchema.parseEpsg(settings.targetEpsg(), "Target EPSG");
    try {
      GeoToolsReferencingRuntime.scoped(() -> decode(target));
      if (settings.sourceMode() == ReprojectSourceMode.EXPLICIT_EPSG) {
        int source = GeometryReprojectSchema.parseEpsg(settings.sourceEpsg(), "Source EPSG");
        operation(source, target);
      }
    } catch (ReprojectDataException e) {
      throw new ReprojectConfigurationException("Invalid fixed CRS configuration: " + e.getMessage(), e);
    }
  }

  /** Returns an independent geometry; transforms XY only and retains every Z/M ordinate. */
  public Geometry reproject(Geometry geometry, Integer sourceOverride, int target)
      throws ReprojectDataException, ReprojectIntegrationException {
    if (geometry == null) {
      return null;
    }
    if (CurveGeometrySupport.containsCurveGeometry(geometry)) {
      throw new ReprojectDataException("GR_CURVE_REQUIRES_LINEARIZATION", "Curved geometry must be explicitly linearized before reprojection");
    }
    final int effectiveSrid;
    try {
      effectiveSrid = GeometrySridSupport.resolveConsistentSrid(geometry);
    } catch (GeometryCodecException e) {
      throw new ReprojectDataException("GR_INVALID_SRID", null, e.getMessage(), e);
    }
    int source = sourceOverride == null ? effectiveSrid : sourceOverride;
    if (source <= 0) {
      throw new ReprojectDataException("GR_MISSING_SOURCE_SRID", "A positive source EPSG is required; geometry SRID is missing");
    }
    MathTransform transform = operation(source, target);
    final Geometry output;
    try {
      output = GeometrySridSupport.copyWithSrid(geometry, target);
    } catch (GeometryCodecException e) {
      throw new ReprojectDataException("GR_UNSUPPORTED_GEOMETRY", null, e.getMessage(), e);
    }
    XYFilter filter = new XYFilter(transform);
    try {
      output.apply(filter);
    } catch (LinkageError | RuntimeException e) {
      throw new ReprojectIntegrationException("The shared geometry/referencing runtime failed during coordinate transformation", e);
    }
    if (filter.error != null) {
      throw filter.error;
    }
    // Explicitly clear cached envelopes for every component, also when the operation is identity.
    output.geometryChanged();
    return output;
  }

  int cachedOperationCount() {
    return operations.size();
  }

  private MathTransform operation(int source, int target)
      throws ReprojectDataException, ReprojectIntegrationException {
    OperationKey key = new OperationKey(source, target);
    MathTransform cached = operations.get(key);
    if (cached != null) {
      return cached;
    }
    return GeoToolsReferencingRuntime.scoped(() -> {
      CoordinateReferenceSystem sourceCrs = decode(source);
      CoordinateReferenceSystem targetCrs = decode(target);
      final CoordinateOperation selected;
      try {
        selected = CRS.getCoordinateOperationFactory(false).createOperation(sourceCrs, targetCrs);
      } catch (OperationNotFoundException e) {
        throw new ReprojectDataException("GR_OPERATION_NOT_FOUND", null,
            "No strict horizontal operation from EPSG:" + source + " to EPSG:" + target, e);
      } catch (FactoryException e) {
        throw new ReprojectIntegrationException("GeoTools could not construct the referencing operation", e);
      }
      MathTransform transform = selected.getMathTransform();
      if (transform.getSourceDimensions() != 2 || transform.getTargetDimensions() != 2) {
        throw new ReprojectDataException("GR_UNSUPPORTED_CRS_DIMENSION", "Coordinate operation must transform exactly two dimensions");
      }
      operationLogger.accept("Geometry Reproject EPSG:" + source + " -> EPSG:" + target
          + "; operation=" + selected.getName() + "; identifiers=" + selected.getIdentifiers()
          + "; accuracy=" + selected.getCoordinateOperationAccuracy());
      operations.put(key, transform);
      return transform;
    });
  }

  private CoordinateReferenceSystem decode(int epsg)
      throws ReprojectDataException, ReprojectIntegrationException {
    try {
      CoordinateReferenceSystem crs = CRS.decode("EPSG:" + epsg, true);
      if (!(crs instanceof GeographicCRS || crs instanceof ProjectedCRS)
          || crs.getCoordinateSystem().getDimension() != 2) {
        throw new ReprojectDataException("GR_UNSUPPORTED_CRS_DIMENSION",
            "EPSG:" + epsg + " must be a two-dimensional geographic or projected CRS");
      }
      return crs;
    } catch (NoSuchAuthorityCodeException e) {
      throw new ReprojectDataException("GR_UNKNOWN_EPSG", null, "Unable to resolve EPSG:" + epsg, e);
    } catch (FactoryException e) {
      throw new ReprojectIntegrationException("GeoTools could not access its EPSG authority database", e);
    }
  }

  private record OperationKey(int source, int target) {}

  private static final class XYFilter implements CoordinateSequenceFilter {
    private final MathTransform transform;
    private final double[] xy = new double[2];
    private ReprojectDataException error;

    private XYFilter(MathTransform transform) {
      this.transform = transform;
    }

    @Override
    public void filter(CoordinateSequence sequence, int index) {
      if (error != null) {
        return;
      }
      xy[0] = sequence.getX(index);
      xy[1] = sequence.getY(index);
      if (!Double.isFinite(xy[0]) || !Double.isFinite(xy[1])) {
        error = new ReprojectDataException("GR_NON_FINITE_COORDINATE", "Source XY coordinates must be finite");
        return;
      }
      try {
        transform.transform(xy, 0, xy, 0, 1);
      } catch (TransformException e) {
        error = new ReprojectDataException("GR_TRANSFORM_FAILED", null, "Coordinate is outside the operation domain or cannot be transformed", e);
        return;
      }
      if (!Double.isFinite(xy[0]) || !Double.isFinite(xy[1])) {
        error = new ReprojectDataException("GR_NON_FINITE_COORDINATE", "Transformed XY coordinates must be finite");
        return;
      }
      sequence.setOrdinate(index, 0, xy[0]);
      sequence.setOrdinate(index, 1, xy[1]);
    }

    @Override
    public boolean isDone() {
      return error != null;
    }

    @Override
    public boolean isGeometryChanged() {
      return true;
    }
  }
}
