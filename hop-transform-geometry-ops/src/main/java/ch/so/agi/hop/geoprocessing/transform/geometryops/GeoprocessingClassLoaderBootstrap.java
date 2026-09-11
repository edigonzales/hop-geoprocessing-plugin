package ch.so.agi.hop.geoprocessing.transform.geometryops;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.value.ValueMetaPluginType;
import org.apache.hop.core.variables.IVariables;

/** Initializes the shared Geometry Type classloader before geoprocessing transforms are loaded. */
@ExtensionPoint(
    id = "GeoprocessingClassLoaderBootstrap",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Initialize the shared Geometry Type classloader for Geoprocessing")
public final class GeoprocessingClassLoaderBootstrap
    implements IExtensionPoint<PluginRegistry> {

  static final String GEOMETRY_CLASSLOADER_GROUP = "sogeo-geometry";
  static final String GEOMETRY_VALUE_META_PLUGIN_ID = "43663879";

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {
    IPlugin geometryPlugin =
        pluginRegistry.findPluginWithId(ValueMetaPluginType.class, GEOMETRY_VALUE_META_PLUGIN_ID);
    if (geometryPlugin == null) {
      throw new HopException(
          "Geoprocessing requires the hop-geometry-type plugin (ValueMeta plugin id "
              + GEOMETRY_VALUE_META_PLUGIN_ID
              + ")");
    }
    if (!GEOMETRY_CLASSLOADER_GROUP.equals(geometryPlugin.getClassLoaderGroup())) {
      throw new HopException(
          "Geoprocessing requires hop-geometry-type to use classloader group '"
              + GEOMETRY_CLASSLOADER_GROUP
              + "' but found '"
              + geometryPlugin.getClassLoaderGroup()
              + "'");
    }
    try {
      pluginRegistry.getClassLoader(geometryPlugin);
    } catch (HopPluginException e) {
      throw new HopException("Unable to initialize the shared Geometry Type classloader", e);
    }
  }
}
