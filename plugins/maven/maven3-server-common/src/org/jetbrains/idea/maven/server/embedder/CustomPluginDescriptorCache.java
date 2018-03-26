/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.plugin.DefaultPluginDescriptorCache;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.component.repository.ComponentDependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class CustomPluginDescriptorCache extends DefaultPluginDescriptorCache {

  private final Map<Key, PluginDescriptor> descriptors = new HashMap<Key, PluginDescriptor>(128);

  public void flush() {
    descriptors.clear();
  }

  public PluginDescriptor get(Key cacheKey) {
    return patchedClone(descriptors.get(cacheKey));
  }

  public void put(Key cacheKey, PluginDescriptor pluginDescriptor) {
    descriptors.put(cacheKey, patchedClone(pluginDescriptor));
  }

  private static PluginDescriptor patchedClone(PluginDescriptor pluginDescriptor) {
    if (pluginDescriptor == null) return null;

    PluginDescriptor clone = clone(pluginDescriptor);
    clone.setDependencies(new ArrayList<ComponentDependency>(pluginDescriptor.getDependencies()));

    return clone;
  }
}
