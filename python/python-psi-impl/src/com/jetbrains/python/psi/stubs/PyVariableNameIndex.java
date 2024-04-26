// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public final class PyVariableNameIndex extends StringStubIndexExtension<PyTargetExpression> {
  public static final StubIndexKey<String, PyTargetExpression> KEY = StubIndexKey.createIndexKey("Py.variable.shortName");

  @Override
  public int getVersion() {
    return super.getVersion() + 1;
  }

  @Override
  @NotNull
  public StubIndexKey<String, PyTargetExpression> getKey() {
    return KEY;
  }

  public static Collection<PyTargetExpression> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyTargetExpression.class);
  }
}
