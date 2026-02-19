// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public final class PyTypeAliasNameIndex extends StringStubIndexExtension<PyTypeAliasStatement> {
  public static final StubIndexKey<String, PyTypeAliasStatement> KEY = StubIndexKey.createIndexKey("Py.TypeAliasName");

  @Override
  public @NotNull StubIndexKey<String, PyTypeAliasStatement> getKey() {
    return KEY;
  }

  public static @NotNull Collection<PyTypeAliasStatement> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyTypeAliasStatement.class);
  }
}
