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
package com.jetbrains.python.hierarchy.call;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author novokrest
 */
public class PyCallerFunctionTreeStructure extends PyCallHierarchyTreeStructureBase {
  public PyCallerFunctionTreeStructure(Project project, PsiElement element, String currentScopeType) {
    super(project, element, currentScopeType);
  }

  @NotNull
  @Override
  protected List<PsiElement> getChildren(@NotNull PyElement element) {
    final List<PsiElement> callers = new ArrayList<>();
    // TODO: Add callers from the dynamic call data manager
    callers.addAll(PyStaticCallHierarchyUtil.getCallers(element));
    return callers;
  }
}
