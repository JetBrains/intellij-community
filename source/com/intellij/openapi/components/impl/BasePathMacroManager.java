package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;

public class BasePathMacroManager extends PathMacroManager {
  private PathMacrosImpl myPathMacros ;

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    result.addMacroExpand(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PathManager.getHomePath());
    myPathMacros.addMacroExpands(result);
    return result;
  }


  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();

    result.addMacroReplacement(PathManager.getHomePath(), PathMacrosImpl.APPLICATION_HOME_MACRO_NAME);
    myPathMacros.addMacroReplacements(result);

    return result;
  }


  public void setPathMacros(final PathMacrosImpl pathMacros) {
    myPathMacros = pathMacros;
  }
}
