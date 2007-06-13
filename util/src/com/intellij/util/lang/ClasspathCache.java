/*
 * @author max
 */
package com.intellij.util.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClasspathCache {
  private final Map<String, List<Loader>> myClassPackagesCache = new HashMap<String, List<Loader>>();
  private final Map<String, List<Loader>> myResourcePackagesCache = new HashMap<String, List<Loader>>();

  public void addResourceEntry(String resourcePath, Loader loader) {
    final List<Loader> loaders = getLoaders(resourcePath);
    if (!loaders.contains(loader)) { // TODO Make linked hash set instead?
      loaders.add(loader);
    }
  }

  public List<Loader> getLoaders(String resourcePath) {
    boolean isClassFile = resourcePath.endsWith(UrlClassLoader.CLASS_EXTENSION);
    final int idx = resourcePath.lastIndexOf('/');
    String packageName = idx > 0 ? resourcePath.substring(0, idx) : "";

    Map<String, List<Loader>> map = isClassFile ? myClassPackagesCache : myResourcePackagesCache;
    List<Loader> list = map.get(packageName);
    if (list == null) {
      list = new ArrayList<Loader>(1);
      map.put(packageName, list);
    }

    return list;
  }
}