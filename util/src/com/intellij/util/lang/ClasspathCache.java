/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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