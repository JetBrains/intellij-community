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
package com.jetbrains.python.actions;

import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


public class PyQualifiedNameProvider implements QualifiedNameProvider {

  @Override
  public PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction ? element : null;
  }

  @Nullable
  @Override
  public String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PyClass) {
      return ((PyClass)element).getQualifiedName();
    }
    if (element instanceof PyFunction) {
      return ((PyFunction)element).getQualifiedName();
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
    final PyClass aClass = PyClassNameIndex.findClass(fqn, project);
    if (aClass != null) {
      return aClass;
    }
    final PyFunction func = findFunctionByQualifiedName(fqn, project);
    if (func != null) {
      return func;
    }
    return null;
  }

  // TODO make it part of PyPsiFacade similarly to createClassByQName()
  @Nullable
  private static PyFunction findFunctionByQualifiedName(@NotNull String qname, @NotNull Project project) {
    final QualifiedName qualifiedName = QualifiedName.fromDottedString(qname);
    final Collection<PyFunction> shortNameMatches = PyFunctionNameIndex.find(qualifiedName.getLastComponent(), project);
    return ContainerUtil.find(shortNameMatches, func -> qname.equals(func.getQualifiedName()));
  }
}
