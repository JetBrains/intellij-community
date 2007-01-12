package com.intellij.openapi.components;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;

public abstract class PathMacroManager {
  public static PathMacroManager getInstance(ComponentManager componentManager) {
    final PathMacroManager component = (PathMacroManager)componentManager.getPicoContainer().getComponentInstanceOfType(PathMacroManager.class);
    assert component != null;
    return component;
  }

  public abstract ExpandMacroToPathMap getExpandMacroMap();
  public abstract ReplacePathToMacroMap getReplacePathMap();
  public abstract void setPathMacros(final PathMacrosImpl pathMacros);

}
