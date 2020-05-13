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
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyDecorator;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Python Decorator stub index.
 * Decorators are indexed by name
 * @author Ilya.Kazakevich
 */
public class PyDecoratorStubIndex extends StringStubIndexExtension<PyDecorator> {
  /**
   * Key to search for python decorators
   */
  public static final StubIndexKey<String, PyDecorator> KEY = StubIndexKey.createIndexKey("Python.Decorator");

  public static Collection<PyDecorator> find(@NotNull String name, @NotNull Project project) {
    return find(name, project, ProjectScope.getAllScope(project));
  }

  public static Collection<PyDecorator> find(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(KEY, name, project, scope, PyDecorator.class);
  }

  @NotNull
  @Override
  public StubIndexKey<String, PyDecorator> getKey() {
    return KEY;
  }
}
