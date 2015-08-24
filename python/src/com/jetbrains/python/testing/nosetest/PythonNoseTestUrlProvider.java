/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.Location;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.testing.PythonTestLocator;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PythonNoseTestUrlProvider implements PythonTestLocator, DumbAware {
  public static final String PROTOCOL_ID = "python_nosetestid";

  public static final PythonNoseTestUrlProvider INSTANCE = new PythonNoseTestUrlProvider();

  @NotNull
  @Override
  public final String getProtocolId() {
    return PROTOCOL_ID;
  }

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    if (!PROTOCOL_ID.equals(protocol)) {
      return Collections.emptyList();
    }

    final List<String> list = StringUtil.split(path, ".");
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    final int listSize = list.size();

    // parse path as [ns.]*fileName[.className][.methodName]
    if (listSize == 2) {
    final List<Location> classes = PythonUnitTestUtil.findLocations(project, list.get(0), list.get(1), null);
    if (classes.size() > 0)
      return classes;
    final List<Location> functions = PythonUnitTestUtil.findLocations(project, list.get(0), null, list.get(1));
    if (functions.size() > 0)
      return functions;
    }
    
    if (listSize > 2) {
      final String className = list.get(listSize - 2);
      final String methodName = list.get(listSize - 1);

      String fileName = list.get(listSize - 3);
      List<Location> locations = PythonUnitTestUtil.findLocations(project, fileName, className, methodName);
      if (locations.size() > 0) {
        return locations;
      }
      locations = PythonUnitTestUtil.findLocations(project, list.get(listSize - 2), list.get(listSize - 1), null);
      if (locations.size() > 0) {
        return locations;
      }
      final List<Location> functions = PythonUnitTestUtil.findLocations(project, list.get(listSize - 2), null, list.get(listSize - 1));
      if (functions.size() > 0)
        return functions;
    }
    return PythonUnitTestUtil.findLocations(project, project.getBasePath() + "/" + StringUtil.join(list, "/") + ".py", null, null);
  }
}
