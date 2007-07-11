package com.intellij.openapi.components;

import com.intellij.application.options.ReplacePathToMacroMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class PathMacroManager implements PathMacroSubstitutor {
  public static PathMacroManager getInstance(@NotNull ComponentManager componentManager) {
    final PathMacroManager component = (PathMacroManager)componentManager.getPicoContainer().getComponentInstanceOfType(PathMacroManager.class);
    assert component != null;
    return component;
  }

  /**
   * @deprecated user expandPaths & collapsePaths instead
   */
  public abstract ReplacePathToMacroMap getReplacePathMap();

  public abstract void expandPaths(Element element);
  public abstract void collapsePaths(Element element);

  public abstract TrackingPathMacroSubstitutor createTrackingSubstitutor();
}
