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
package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyiModuleMembersProvider extends PyModuleMembersProvider {
  @Nullable
  @Override
  public PsiElement resolveMember(PyFile module, String name) {
    final PsiElement pythonStub = PyiUtil.getPythonStub(module);
    if (pythonStub instanceof PyFile) {
      final PyFile stubFile = (PyFile)pythonStub;
      final PsiElement member = stubFile.getElementNamed(name);
      if (member != null && isExportedName(stubFile, name)) {
        return member;
      }
    }
    return null;
  }

  @Override
  public Collection<PyCustomMember> getMembers(PyFile module, PointInImport point) {
    final PsiElement pythonStub = PyiUtil.getPythonStub(module);
    if (pythonStub instanceof PyFile) {
      final PyFile stubFile = (PyFile)pythonStub;
      final List<PyCustomMember> results = new ArrayList<>();
      for (PyElement element : stubFile.iterateNames()) {
        final String name = element.getName();
        if (name != null && isExportedName(stubFile, name)) {
          results.add(new PyCustomMember(name, element));
        }
      }
      return results;
    }
    return Collections.emptyList();
  }

  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
    return null;
  }

  private static boolean isExportedName(@NotNull PyFile file, @NotNull String name) {
    final PyImportElement importElement = findImportElementByName(file, name);
    return importElement == null || isExportedImportElement(importElement);
  }

  private static boolean isExportedImportElement(@NotNull PyImportElement element) {
    return element.getAsNameElement() != null;
  }

  @Nullable
  private static PyImportElement findImportElementByName(@NotNull PyFile file, @NotNull String name) {
    for (PyImportElement element : getImportElements(file)) {
      if (name.equals(element.getVisibleName())) {
        return element;
      }
    }
    return null;
  }

  private static List<PyImportElement> getImportElements(@NotNull PyFile file) {
    final List<PyImportElement> result = new ArrayList<>();
    result.addAll(file.getImportTargets());
    for (PyFromImportStatement statement : file.getFromImports()) {
      result.addAll(Arrays.asList(statement.getImportElements()));
    }
    return result;
  }
}
