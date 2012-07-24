/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.javaee;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ResourceRegistrarImpl implements ResourceRegistrar {

  private final Map<String, Map<String, ExternalResourceManagerImpl.Resource>> myResources = new HashMap<String, Map<String, ExternalResourceManagerImpl.Resource>>();
  private final List<String> myIgnored = new ArrayList<String>();

  public void addStdResource(@NonNls String resource, @NonNls String fileName) {
    addStdResource(resource, null, fileName, getClass());
  }

  public void addStdResource(@NonNls String resource, @NonNls String fileName, Class klass) {
    addStdResource(resource, null, fileName, klass);
  }

  public void addStdResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, @Nullable Class klass, @Nullable ClassLoader classLoader) {
    final Map<String, ExternalResourceManagerImpl.Resource> map = ExternalResourceManagerImpl.getMap(myResources, version, true);
    assert map != null;
    ExternalResourceManagerImpl.Resource res = new ExternalResourceManagerImpl.Resource();
    res.file = fileName;
    res.classLoader = classLoader;
    res.clazz = klass;
    map.put(resource, res);
  }

  public void addStdResource(@NonNls String resource, @Nullable @NonNls String version, @NonNls String fileName, Class klass) {
    addStdResource(resource, version, fileName, klass, null);
  }

  public void addIgnoredResource(@NonNls String url) {
    myIgnored.add(url);
  }

  public void addInternalResource(@NonNls String resource, @NonNls String fileName) {
    addInternalResource(resource, null, fileName, getClass());
  }

  public void addInternalResource(@NonNls String resource, @NonNls String fileName, Class clazz) {
    addInternalResource(resource, null, fileName, clazz);
  }

  public void addInternalResource(@NonNls String resource, @NonNls String version, @NonNls String fileName) {
    addInternalResource(resource, version, fileName, getClass());
  }

  public void addInternalResource(@NonNls String resource, @Nullable @NonNls String version, @NonNls String fileName, @Nullable Class clazz) {
    addStdResource(resource, version, ExternalResourceManagerImpl.STANDARD_SCHEMAS + fileName, clazz);
  }

  public Map<String, Map<String, ExternalResourceManagerImpl.Resource>> getResources() {
    return myResources;
  }

  public List<String> getIgnored() {
    return myIgnored;
  }
}
