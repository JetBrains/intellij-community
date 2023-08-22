// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PyFunctionNameIndex extends StringStubIndexExtension<PyFunction> {
  public static final StubIndexKey<String, PyFunction> KEY = StubIndexKey.createIndexKey("Py.function.shortName");

  @Override
  @NotNull
  public StubIndexKey<String, PyFunction> getKey() {
    return KEY;
  }

  @NotNull
  public static Collection<PyFunction> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyFunction.class);
  }

  @NotNull
  public static Collection<PyFunction> find(String name, Project project) {
    return StubIndex.getElements(KEY, name, project, ProjectScope.getAllScope(project), PyFunction.class);
  }

  public static Collection<String> allKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(KEY, project);
  }
}