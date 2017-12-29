// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyiModuleMembersProvider extends PyModuleMembersProvider {
  @Nullable
  @Override
  public PsiElement resolveMember(@NotNull PyFile module, @NotNull String name, @NotNull PyResolveContext resolveContext) {
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
  @NotNull
  public Collection<PyCustomMember> getMembers(@NotNull PyFile module, @NotNull PointInImport point, @NotNull TypeEvalContext context) {
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
    // This method will be removed in 2018.2
    return Collections.emptyList();
  }

  @Override
  @NotNull
  protected Collection<PyCustomMember> getMembersByQName(@NotNull PyFile module, @NotNull String qName, @NotNull TypeEvalContext context) {
    return Collections.emptyList();
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
    final List<PyImportElement> result = new ArrayList<>(file.getImportTargets());
    for (PyFromImportStatement statement : file.getFromImports()) {
      result.addAll(Arrays.asList(statement.getImportElements()));
    }
    return result;
  }
}
