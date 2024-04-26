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

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class PyUpdatePropertySignatureQuickFix extends PsiUpdateModCommandQuickFix {
  private final boolean myHasValue;

  public PyUpdatePropertySignatureQuickFix(boolean hasValue) {
    myHasValue = hasValue;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.update.parameters");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyCallable function = PsiTreeUtil.getParentOfType(element, PyCallable.class, false);
    assert function != null;
    final PyParameterList parameterList = function.getParameterList();
    final PyParameter[] parameters = parameterList.getParameters();
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    String selfText = parameters.length != 0 ? parameters[0].getText() : PyNames.CANONICAL_SELF;
    final StringBuilder functionText = new StringBuilder("def foo(" + selfText);
    if (myHasValue) {
      String valueText = parameters.length > 1 ? parameters[1].getText() : "value";
      functionText.append(", ").append(valueText);
    }
    functionText.append("): pass");

    final PyParameterList list = generator.createFromText(LanguageLevel.forElement(element),
                                                          PyFunction.class, functionText.toString()).getParameterList();
    parameterList.replace(list);
  }
}
