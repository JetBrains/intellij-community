package com.intellij.openapi.components.ex;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;

import java.util.Map;

/**
 * @author max
 */
public interface ComponentManagerEx extends ComponentManager {
  /**
   * @deprecated Use {@link #registerComponent(com.intellij.openapi.components.ComponentConfig)} istead
   */
  void registerComponent(Class interfaceClass, Class implementationClass);

  /**
   * @deprecated Use {@link #registerComponent(com.intellij.openapi.components.ComponentConfig)} istead
   */
  void registerComponent(Class interfaceClass, Class implementationClass, Map options);

  void registerComponent(ComponentConfig config);
  void registerComponent(ComponentConfig config, IdeaPluginDescriptor pluginDescriptor);

  IComponentStore getComponentStore();
}
