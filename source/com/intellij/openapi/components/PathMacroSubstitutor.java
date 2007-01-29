package com.intellij.openapi.components;

import org.w3c.dom.Element;

public interface PathMacroSubstitutor {
  String expandPath(String path);
  String collapsePath(String path);

  void expandPaths(Element element);
  void collapsePaths(Element element);
}
