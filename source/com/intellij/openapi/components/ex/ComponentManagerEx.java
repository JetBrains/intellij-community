package com.intellij.openapi.components.ex;

import com.intellij.openapi.components.ComponentManager;
import org.jdom.Element;

import java.io.InputStream;
import java.util.Map;

/**
 * @author max
 */
public interface ComponentManagerEx extends ComponentManager {
  void registerComponent(Class interfaceClass, Class implementationClass);
  void registerComponent(Class interfaceClass, Class implementationClass, Map options);
}
