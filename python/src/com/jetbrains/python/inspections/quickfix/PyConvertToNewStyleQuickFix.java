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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PyConvertToNewStyleQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.convert.to.new.style");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    assert pyClass != null;

    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyArgumentList expressionList = pyClass.getSuperClassExpressionList();
    if (expressionList != null) {
      final PyExpression object = generator.createExpressionFromText(LanguageLevel.forElement(element), "object");
      expressionList.addArgumentFirst(object);
    }
    else {
      final PyArgumentList list =
        generator.createFromText(LanguageLevel.forElement(element), PyClass.class, "class A(object):pass").getSuperClassExpressionList();
      assert list != null;
      final ASTNode node = pyClass.getNameNode();
      assert node != null;
      pyClass.addAfter(list, node.getPsi());
    }
  }
}
