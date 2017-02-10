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
package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Alexey.Ivanov
 */
public class PyIntroduceConstantHandler extends IntroduceHandler {
  public PyIntroduceConstantHandler() {
    super(new ConstantValidator(), PyBundle.message("refactoring.introduce.constant.dialog.title"));
  }

  @Override
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    if (PsiTreeUtil.getParentOfType(expression, ScopeOwner.class) instanceof PyFile) {
      return super.replaceExpression(expression, newExpression, operation);
    }
    return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
  }

  @Override
  protected PsiElement addDeclaration(@NotNull final PsiElement expression,
                                      @NotNull final PsiElement declaration,
                                      @NotNull final IntroduceOperation operation) {
    final PsiElement anchor = expression.getContainingFile();
    assert anchor instanceof PyFile;
    return anchor.addBefore(declaration, AddImportHelper.getFileInsertPosition((PyFile)anchor));
  }

  @Override
  protected Collection<String> generateSuggestedNames(@NotNull final PyExpression expression) {
    Collection<String> names = new HashSet<>();
    for (String name : super.generateSuggestedNames(expression)) {
      names.add(StringUtil.toUpperCase(name));
    }
    return names;
  }

  @Override
  protected boolean isValidIntroduceContext(PsiElement element) {
    return super.isValidIntroduceContext(element) || PsiTreeUtil.getParentOfType(element, PyParameterList.class) != null;
  }

  @Override
  protected String getHelpId() {
    return "python.reference.introduceConstant";
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.python.introduce.constant";
  }
}
