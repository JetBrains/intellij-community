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
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PyClass ||
           (element instanceof PyFile && PyUtil.isPackage((PyFile)element)) ||
           element instanceof PyImportedModule ||
           element instanceof PyFunction ||
           element instanceof PyTargetExpression;
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof PyImportedModule) {
      final PsiElement resolved = ((PyImportedModule)element).resolve();
      if (resolved != null) {
        element = resolved;
      }
    }
    if (element instanceof PsiFileSystemItem) {
      return new PyModuleFindUsagesHandler((PsiFileSystemItem)element);
    }
    if (element instanceof PyFunction) {
      if (!forHighlightUsages) {
        TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), null);
        final Collection<PsiElement> superMethods = PySuperMethodsSearch.search((PyFunction)element, true, context).findAll();
        if (superMethods.size() > 0) {
          final PsiElement next = superMethods.iterator().next();
          // TODO should do this for Jython functions overriding Java methods too
          if (next instanceof PyFunction && !isInObject((PyFunction)next)) {
            int rc = Messages.showYesNoDialog(element.getProject(), "Method " +
                                                                          ((PyFunction)element).getName() +
                                                                          " overrides method of class " +
                                                                          ((PyFunction)next).getContainingClass().getName() +
                                                                          ".\nDo you want to find usages of the base method?",  "Find Usages", Messages.getQuestionIcon());
            if (rc == Messages.YES) {
              List<PsiElement> allMethods = new ArrayList<>();
              allMethods.add(element);
              allMethods.addAll(superMethods);
              return new PyFunctionFindUsagesHandler(element, allMethods);
            }
            else {
              return new PyFunctionFindUsagesHandler(element);
            }
          }
        }

      }
      return new PyFunctionFindUsagesHandler(element);
    }
    if (element instanceof PyClass) {
      return new PyClassFindUsagesHandler((PyClass)element);
    }
    if (element instanceof PyTargetExpression) {
      return new PyTargetExpressionFindUsagesHandler(((PyTargetExpression)element));
    }
    return null;
  }

  private static boolean isInObject(PyFunction fun) {
    final PyClass containingClass = fun.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    return PyUtil.isObjectClass(containingClass);
  }
}
