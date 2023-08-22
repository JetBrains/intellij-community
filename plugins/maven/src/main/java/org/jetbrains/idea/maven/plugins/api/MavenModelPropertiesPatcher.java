// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SingletonInstancesCache;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class MavenModelPropertiesPatcher {

  /*
   * Add properties those should be added by plugins.
   */
  public static void patch(Properties modelProperties, @Nullable Collection<? extends MavenPlugin> plugins) {
    if (plugins == null) return;

    Map<String, Map<String, Map<String, List<MavenPluginDescriptor>>>> map = MavenPluginDescriptor.getDescriptorsMap();

    for (MavenPlugin plugin : plugins) {
      Map<String, Map<String, List<MavenPluginDescriptor>>> groupMap = map.get(plugin.getArtifactId());
      if (groupMap != null) {
        Map<String, List<MavenPluginDescriptor>> goalsMap = groupMap.get(plugin.getGroupId());
        if (goalsMap != null) {
          patch(modelProperties, goalsMap.get(null), null, plugin.getConfigurationElement(), plugin);

          for (MavenPlugin.Execution execution : plugin.getExecutions()) {
            for (String goal : execution.getGoals()) {
              patch(modelProperties, goalsMap.get(goal), goal, execution.getConfigurationElement(), plugin);
            }
          }
        }
      }
    }
  }

  private static void patch(Properties modelProperties, @Nullable List<MavenPluginDescriptor> descriptors, @Nullable String goal, Element cfgElement, MavenPlugin plugin) {
    if (descriptors == null) {
      return;
    }

    for (MavenPluginDescriptor descriptor : descriptors) {
      if (descriptor.properties != null) {
        for (MavenPluginDescriptor.ModelProperty property : descriptor.properties) {
          if (!property.insideConfigurationOnly && StringUtil.isNotEmpty(property.name)) {
            modelProperties.setProperty(property.name, StringUtil.notNullize(property.value));
          }
        }
      }

      if (descriptor.propertyGenerator != null) {
        MavenPropertiesGenerator generator = SingletonInstancesCache.getInstance(descriptor.propertyGenerator,
                                                                                 descriptor.getPluginDescriptor().getClassLoader());
        generator.generate(modelProperties, goal, plugin, cfgElement);
      }
    }
  }

}
