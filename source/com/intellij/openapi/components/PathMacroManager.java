package com.intellij.openapi.components;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;

import java.util.Set;

public abstract class PathMacroManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.PathMacroManager");

  public static boolean ourLogMacroUsage = false;

  private static final Set<String> ourUsedMacros = new HashSet<String>();

  public static PathMacroManager getInstance(ComponentManager componentManager) {
    final PathMacroManager component = (PathMacroManager)componentManager.getPicoContainer().getComponentInstanceOfType(PathMacroManager.class);
    assert component != null;
    return component;
  }

  public abstract ReplacePathToMacroMap getReplacePathMap();
  public abstract void setPathMacros(final PathMacrosImpl pathMacros);


  public abstract String expandPath(String path);
  public abstract String collapsePath(String path);

  public abstract void expandPaths(Element element);
  public abstract void collapsePaths(Element element);

  public static String[] getUsedMacroNames() {
    return getUsedMacros().toArray(new String[getUsedMacros().size()]);
  }

  public static void startLoggingUsedMacros() {
    LOG.assertTrue(!ourLogMacroUsage, "endLoggingUsedMacros() must be called before calling startLoggingUsedMacros()");
    getUsedMacros().clear();
    ourLogMacroUsage = true;
  }

  public static boolean isMacroLoggingEnabled() {
    return ourLogMacroUsage;
  }

  public static void endLoggingUsedMacros() {
    LOG.assertTrue(ourLogMacroUsage, "startLoggingUsedMacros() must be called before calling endLoggingUsedMacros()");
    ourLogMacroUsage = false;
  }

  public static Set<String> getUsedMacros() {
    return ourUsedMacros;
  }
}
