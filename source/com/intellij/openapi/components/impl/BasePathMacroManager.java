package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BasePathMacroManager extends PathMacroManager {
  private PathMacrosImpl myPathMacros;
  private boolean myUseUserMacroses;


  public BasePathMacroManager(boolean useUserMacroses) {
    myUseUserMacroses = useUserMacroses;
  }

  public BasePathMacroManager() {
    this(true);
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    result.addMacroExpand(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PathManager.getHomePath());
    if (myUseUserMacroses) {
      getPathMacros().addMacroExpands(result);
    }
    return result;
  }


  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();

    result.addMacroReplacement(PathManager.getHomePath(), PathMacrosImpl.APPLICATION_HOME_MACRO_NAME);
    if (myUseUserMacroses) {
      getPathMacros().addMacroReplacements(result);
    }

    return result;
  }

  public TrackingPathMacroSubstitutor createTrackingSubstitutor() {
    return new MyTrackingPathMacroSubstitutor(new HashSet<String>());
  }

  public String expandPath(final String path) {
    return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public String collapsePath(final String path) {
    return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public void expandPaths(final Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }


  public void collapsePaths(final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public PathMacrosImpl getPathMacros() {
    if (myPathMacros == null) {
      myPathMacros = PathMacrosImpl.getInstanceEx();
    }

    return myPathMacros;
  }


  private class MyTrackingPathMacroSubstitutor implements TrackingPathMacroSubstitutor {
    private Set<String> myUsedMacros;

    public MyTrackingPathMacroSubstitutor(final Set<String> usedMacros) {
      myUsedMacros = usedMacros;
    }

    public Set<String> getUsedMacros() {
      return Collections.unmodifiableSet(myUsedMacros);
    }

    public void reset() {
      myUsedMacros.clear();
    }

    public String expandPath(final String path) {
      return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive, myUsedMacros);
    }

    public String collapsePath(final String path) {
      return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive, myUsedMacros);
    }

    public void expandPaths(final Element element) {
      getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, myUsedMacros);
    }

    public void collapsePaths(final Element element) {
      getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, myUsedMacros);
    }

  }
}
