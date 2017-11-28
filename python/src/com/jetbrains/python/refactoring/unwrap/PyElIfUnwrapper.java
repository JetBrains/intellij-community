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
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementWithElse;
import com.jetbrains.python.psi.impl.PyIfPartElifImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User : ktisha
 */
public class PyElIfUnwrapper extends PyUnwrapper {
  public PyElIfUnwrapper() {
    super(PyBundle.message("unwrap.elif"));
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context)
    throws IncorrectOperationException {
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PyStatementWithElse.class);
    context.extractPart(element);
    context.delete(parent);
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return PsiTreeUtil.getParentOfType(e, PyStatementWithElse.class);
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (e instanceof PyIfPartElifImpl) {
      final PyStatementList statementList = ((PyIfPartElifImpl)e).getStatementList();
      if (statementList != null) {
        final PyStatement[] statements = statementList.getStatements();
        return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
      }
    }
    return false;
  }
}
