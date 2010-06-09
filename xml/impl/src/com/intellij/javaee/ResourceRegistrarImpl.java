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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ResourceRegistrarImpl implements ResourceRegistrar {

  private final Map<String, Map<String, String>> myResources = new HashMap<String, Map<String, String>>();
  private final List<String> myIgnored = new ArrayList<String>();

  public void addStdResource(@NonNls String resource, @NonNls String fileName) {
    addStdResource(resource, null, fileName, getClass());
  }

  public void addStdResource(@NonNls String resource, @NonNls String fileName, Class klass) {
    addStdResource(resource, null, fileName, klass);
  }

  public void addStdResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, Class klass) {
    final String file = getFile(fileName, klass);
    if (file != null) {
      final Map<String, String> map = ExternalResourceManagerImpl.getMap(myResources, version, true);
      assert map != null;
      map.put(resource, file);
    }
    else {
      String message = "Cannot find standard resource. filename:" + fileName + " klass=" + klass;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ExternalResourceManagerImpl.LOG.error(message);
      }
      else {
        ExternalResourceManagerImpl.LOG.warn(message);
      }
    }
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

  public void addInternalResource(@NonNls String resource, @NonNls String version, @NonNls String fileName, Class clazz) {
    addStdResource(resource, version, ExternalResourceManagerImpl.STANDARD_SCHEMAS + fileName, clazz);
  }

  @Nullable
  private static String getFile(String name, Class klass) {
    final URL resource = klass.getResource(name);
    if (resource == null) return null;

    String path = FileUtil.unquote(resource.toString());
    // this is done by FileUtil for windows
    path = path.replace('\\','/');
    return path;
  }

  public Map<String, Map<String, String>> getResources() {
    return myResources;
  }

  public List<String> getIgnored() {
    return myIgnored;
  }
}
