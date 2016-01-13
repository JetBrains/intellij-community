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

import com.google.common.collect.Iterables;
import com.intellij.ide.actions.QualifiedNameProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


/**
 * User: anna
 * Date: 3/29/11
 */
public class PyQualifiedNameProvider implements QualifiedNameProvider {
  @Override
  public PsiElement adjustElementToCopy(PsiElement element) {
    return element instanceof PyClass || element instanceof PyFunction ? element : null;
  }

  @Nullable
  @Override
  public String getQualifiedName(PsiElement element) {
    if (element instanceof PyClass) {
      return ((PyClass)element).getQualifiedName();
    }
    if (element instanceof PyFunction) {
      final PyClass containingClass = ((PyFunction)element).getContainingClass();
      if (containingClass != null) {
        return containingClass.getQualifiedName() + "#" + ((PyFunction)element).getName();
      }
      else {
        return ((PyFunction)element).getQualifiedName();
      } 
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement qualifiedNameToElement(String fqn, Project project) {
    PyClass aClass = PyClassNameIndex.findClass(fqn, project);
    if (aClass != null) {
      return aClass;
    }
    final Collection<PyFunction> functions = PyFunctionNameIndex.find(fqn, project);
    if (!functions.isEmpty()) {
      return ContainerUtil.getFirstItem(functions);
    }
    final int sharpIdx = fqn.indexOf("#");
    if (sharpIdx > -1) {
      final String className = StringUtil.getPackageName(fqn, '#');
      aClass = PyClassNameIndex.findClass(className, project);
      if (aClass != null) {
        final String memberName = StringUtil.getShortName(fqn, '#');
        final PyClass nestedClass = aClass.findNestedClass(memberName, false);
        if (nestedClass != null) return nestedClass;
        final PyFunction methodByName = aClass.findMethodByName(memberName, false, null);
        if (methodByName != null) return methodByName;
      }
    }
    return null;
  }

  @Override
  public void insertQualifiedName(String fqn, PsiElement element, Editor editor, Project project) {
    EditorModificationUtil.insertStringAtCaret(editor, fqn);
  }
}
