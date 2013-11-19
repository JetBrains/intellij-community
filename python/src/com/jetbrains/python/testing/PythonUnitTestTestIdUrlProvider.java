/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testIntegration.TestLocationProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PythonUnitTestTestIdUrlProvider implements TestLocationProvider {
  @NonNls
  private static final String PROTOCOL_ID = "python_uttestid";

  @NotNull
  public List<Location> getLocation(@NotNull final String protocolId, @NotNull final String path,
                                    final Project project) {
    if (!PROTOCOL_ID.equals(protocolId)) {
      return Collections.emptyList();
    }

    final List<String> list = StringUtil.split(path, ".");
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    final int listSize = list.size();

    // parse path as [ns.]*fileName.className[.methodName]

    if (listSize == 2) {
      return findLocations(project, list.get(0), list.get(1), null);
    }
    if (listSize > 2) {
      final String className = list.get(listSize - 2);
      final String methodName = list.get(listSize - 1);

      String fileName = list.get(listSize - 3);
      final List<Location> locations = findLocations(project, fileName, className, methodName);
      if (locations.size() > 0) {
        return locations;
      }
      return findLocations(project, list.get(listSize-2), list.get(listSize-1), null);
    }
    return Collections.emptyList();
  }


  private static List<Location> findLocations(Project project,
                                              String fileName,
                                              String className,
                                              @Nullable String methodName) {
    if (fileName.indexOf("%") >= 0) {
      fileName = fileName.substring(0, fileName.lastIndexOf("%"));
    }

    final List<Location> locations = new ArrayList<Location>();
    for (PyClass cls : PyClassNameIndex.find(className, project, false)) {
      ProgressManager.checkCanceled();

      final PsiFile containingFile = cls.getContainingFile();
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      final String clsFileName = virtualFile == null? containingFile.getName() : virtualFile.getPath();
      final String clsFileNameWithoutExt = FileUtil.getNameWithoutExtension(clsFileName);
      if (!clsFileNameWithoutExt.endsWith(fileName)) {
        continue;
      }
      if (methodName == null) {
        locations.add(new PsiLocation<PyClass>(project, cls));
      }
      else {
        final PyFunction method = cls.findMethodByName(methodName, true);
        if (method == null) {
          continue;
        }

        locations.add(new PsiLocation<PyFunction>(project, method));
      }
    }

    return locations;
  }
}
