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
package com.jetbrains.python.refactoring.introduce;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:20:13 PM
 */
public abstract class IntroduceValidator {
  private final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());

  public boolean isNameValid(final String name, final Project project) {
    return (name != null) &&
           (myNamesValidator.isIdentifier(name, project)) &&
           !(myNamesValidator.isKeyword(name, project));
  }

  public boolean checkPossibleName(@NotNull final String name, @NotNull final PyExpression expression) {
    return check(name, expression) == null;
  }

  @Nullable
  public abstract String check(String name, PsiElement psiElement);

  public static boolean isDefinedInScope(String name, PsiElement psiElement) {
    if (psiElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE) != null) {
      final Pair<PsiElement,TextRange> data = psiElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
      psiElement = data.first;
    }
    PsiElement context = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    }
    if (context == null) {
      context = psiElement.getContainingFile();
    }

    return PyRefactoringUtil.collectUsedNames(context).contains(name) || PyBuiltinCache.getInstance(psiElement).getByName(name) != null;
  }
}
