/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.configuration;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author yole
*/
public class VirtualEnvProjectFilter implements Predicate<Sdk> {
  private final String myBasePath;

  public VirtualEnvProjectFilter(@Nullable final String basePath) {
    myBasePath = basePath;
  }

  @Override
  public boolean apply(@Nullable final Sdk input) {
    if (input != null && PythonSdkType.isVirtualEnv(input)) {
      PythonSdkAdditionalData data = (PythonSdkAdditionalData) input.getSdkAdditionalData();
      if (data != null) {
        final String path = data.getAssociatedProjectPath();
        if (path != null && (myBasePath == null || !path.equals(myBasePath))) {
          return true;
        }
      }
    }
    return false;
  }

  @Contract("null, _ -> false")
  public static boolean removeNotMatching(Project project, List<Sdk> sdks) {
    if (project != null) {
      final String basePath = project.getBasePath();
      if (basePath != null) {
        return Iterables.removeIf(sdks, new VirtualEnvProjectFilter(FileUtil.toSystemIndependentName(basePath)));
      }
    }
    return false;
  }

  public static void removeAllAssociated(List<Sdk> sdks) {
    Iterables.removeIf(sdks, new VirtualEnvProjectFilter(null));
  }
}
