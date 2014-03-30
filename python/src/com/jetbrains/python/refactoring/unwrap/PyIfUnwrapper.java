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
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyIfPartIfImpl;

import java.util.List;

/**
 * User : ktisha
 */
public class PyIfUnwrapper extends PyUnwrapper {
  public PyIfUnwrapper() {
    super(PyBundle.message("unwrap.if"));
  }

  public boolean isApplicableTo(PsiElement e) {
    if (e instanceof PyIfPartIfImpl) {
      final PyStatementList statementList = ((PyIfPartIfImpl)e).getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return PsiTreeUtil.getParentOfType(e, PyIfStatement.class);
  }


  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
    context.extractPart(ifStatement);
    context.delete(ifStatement);
  }
}
