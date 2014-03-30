/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement || psiElement instanceof PyReferenceExpression;
  }

  public String getHelpId(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PyClass) {
      return "reference.dialogs.findUsages.class";
    }
    if (psiElement instanceof PyFunction) {
      return "reference.dialogs.findUsages.method";
    }
    if (psiElement instanceof PyReferenceExpression || psiElement instanceof PyTargetExpression || psiElement instanceof PyParameter) {
      return "reference.dialogs.findUsages.variable";
    }
    return null;
  }

  @NotNull
  public String getType(@NotNull PsiElement element) {
    if (element instanceof PyNamedParameter) return "parameter";  //TODO: replace strings to messages
    if (element instanceof PyFunction) {
      if (((PyFunction) element).getContainingClass() != null) {
        return "method";
      }
      return "function";
    }
    if (element instanceof PyClass) return "class";
    if (element instanceof PyReferenceExpression) return "variable";
    if (element instanceof PyTargetExpression) {
      final PyImportElement importElement = PsiTreeUtil.getParentOfType(element, PyImportElement.class);
      if (importElement != null && importElement.getAsNameElement() == element) {
        return "imported module alias";
      }
      return "variable";
    }
    if (element instanceof PyKeywordArgument) {
      return "keyword argument";
    }
    return "";
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)element).getName();
      return name == null ? "<unnamed>" : name;
    }
    if (element instanceof PyReferenceExpression) {
      String referencedName = ((PyReferenceExpression)element).getReferencedName();
      if (referencedName == null) {
        return "<unnamed>";
      }
      return referencedName;
    }
    return "";
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    if (element instanceof PyNamedParameter) {
      StringBuilder result = new StringBuilder(((PyNamedParameter)element).getName());
      final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      if (function != null) {
        result.append(" of ");
        appendFunctionDescription(result, function);
      }
      return result.toString();
    }
    if (element instanceof PyFunction) {
      StringBuilder result = new StringBuilder();
      appendFunctionDescription(result, (PyFunction)element);
      return result.toString();
    }
    return getDescriptiveName(element);
  }

  private static void appendFunctionDescription(StringBuilder result, PyFunction function) {
    result.append(function.getName()).append("()");
    final PyClass containingClass = function.getContainingClass();
    if (containingClass != null) {
      result.append(" of class ").append(containingClass.getName());
    }
  }

  public WordsScanner getWordsScanner() {
    return new PyWordsScanner();
  }
}
