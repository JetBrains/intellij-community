// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


@ApiStatus.Internal
public final class PyClassNameIndexInsensitive extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortNameInsensitive");

  @Override
  public @NotNull StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project) {
    return StubIndex.getElements(KEY, StringUtil.toLowerCase(name), project, ProjectScope.getProjectScope(project), PyClass.class);
  }
}
