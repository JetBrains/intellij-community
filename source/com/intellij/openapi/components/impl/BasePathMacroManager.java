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
import java.util.Set;
import java.util.TreeSet;

public class BasePathMacroManager extends PathMacroManager {
  private PathMacrosImpl myPathMacros;


  public BasePathMacroManager() {
    myPathMacros = PathMacrosImpl.getInstanceEx();
  }

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

  public TrackingPathMacroSubstitutor createTrackingSubstitutor() {
    return new MyTrackingPathMacroSubstitutor();
  }

  public String expandPath(final String path) {
    return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public String collapsePath(final String path) {
    return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public void expandPaths(final org.w3c.dom.Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public void collapsePaths(final org.w3c.dom.Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }

  public void expandPaths(final Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }


  public void collapsePaths(final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, null);
  }


  private class MyTrackingPathMacroSubstitutor implements TrackingPathMacroSubstitutor {
    private Set<String> myUsedMacros = new TreeSet<String>();

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
