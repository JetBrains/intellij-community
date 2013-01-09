package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenPlugin;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenModelPropertiesPatcher {

  private static volatile Map<String, Map<String, String[]>> ourMap;

  private static Map<String, Map<String, String[]>> getMap() {
    Map<String, Map<String, String[]>> res = ourMap;

    if (res == null) {
      res = new HashMap<String, Map<String, String[]>>();

      for (MavenPluginDescriptor pluginDescriptor : MavenPluginDescriptor.EP_NAME.getExtensions()) {
        if (pluginDescriptor.properties != null && pluginDescriptor.properties.length > 0) {
          Pair<String, String> pluginId = MavenPluginDescriptor.parsePluginId(pluginDescriptor.mavenId);

          String[] properties = new String[pluginDescriptor.properties.length];
          for (int i = 0; i < pluginDescriptor.properties.length; i++) {
            properties[i] = pluginDescriptor.properties[i].name;
          }

          Map<String, String[]> groupMap = res.get(pluginId.second);// pluginId.second is artifactId
          if (groupMap == null) {
            groupMap = new HashMap<String, String[]>();
            res.put(pluginId.second, groupMap);
          }

          groupMap.put(pluginId.first, properties); // pluginId.first is groupId
        }
      }

      ourMap = res;
    }

    return res;
  }

  /*
   * Add properties those should be added by plugins.
   */
  public static void patch(Properties modelProperties, @Nullable Collection<MavenPlugin> plugins) {
    if (plugins == null) return;

    Map<String, Map<String, String[]>> map = getMap();

    for (MavenPlugin plugin : plugins) {
      Map<String, String[]> groupMap = map.get(plugin.getArtifactId());
      if (groupMap != null) {
        String[] properties = groupMap.get(plugin.getGroupId());

        if (properties != null) {
          for (String property : properties) {
            if (!modelProperties.containsKey(property)) {
              modelProperties.setProperty(property, "");
            }
          }
        }
      }
    }
  }

}
