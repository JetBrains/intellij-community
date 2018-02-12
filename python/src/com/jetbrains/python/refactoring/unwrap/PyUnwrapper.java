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

import com.intellij.codeInsight.unwrap.AbstractUnwrapper;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyIfPartIfImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : ktisha
 */
public abstract class PyUnwrapper extends AbstractUnwrapper<PyUnwrapper.Context> {

  public PyUnwrapper(String description) {
    super(description);
  }

  @Override
  protected Context createContext() {
    return new Context();
  }

  @NotNull
  @Override
  public List<PsiElement> unwrap(@NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    List<PsiElement> res = super.unwrap(editor, element);
    for (PsiElement e : res) {
      CodeEditUtil.markToReformat(e.getNode(), true);
    }
    return res;
  }


  protected static class Context extends AbstractUnwrapper.AbstractContext {
    public void extractPart(@Nullable PsiElement from) {
      if (from instanceof PyStatementWithElse) {
        extractFromConditionalBlock((PyStatementWithElse)from);
      }
      else if (from instanceof PyStatementPart) {
        extractFromElseBlock((PyStatementPart)from);
      }
      else if (from instanceof PyWithStatement) {
        extractFromWithBlock((PyWithStatement)from);
      }
    }

    public void extractFromConditionalBlock(PyStatementWithElse from) {
      PyStatementList statementList = null;
      if (from instanceof PyIfStatement) {
        final PyIfPart ifPart = ((PyIfStatement)from).getIfPart();
        if (ifPart instanceof PyIfPartIfImpl) {
          statementList = ifPart.getStatementList();
        }
      }
      else if (from instanceof PyWhileStatement) {
        final PyWhilePart part = ((PyWhileStatement)from).getWhilePart();
        statementList = part.getStatementList();
      }
      else if (from instanceof PyTryExceptStatement) {
        final PyTryPart part = ((PyTryExceptStatement)from).getTryPart();
        statementList = part.getStatementList();
      }
      else if (from instanceof PyForStatement) {
        final PyForPart part = ((PyForStatement)from).getForPart();
        statementList = part.getStatementList();
      }
      if (statementList != null)
        extract(statementList.getFirstChild(), statementList.getLastChild(), from);
    }

    public void extractFromElseBlock(PyStatementPart from) {
      PyStatementList body = from.getStatementList();
      if (body != null)
        extract(body.getFirstChild(), body.getLastChild(), from.getParent());
    }

    public void extractFromWithBlock(PyWithStatement from) {
      ASTNode n = from.getNode().findChildByType(PyElementTypes.STATEMENT_LISTS);
      if (n != null) {
        final PyStatementList body = (PyStatementList)n.getPsi();
        if (body != null)
          extract(body.getFirstChild(), body.getLastChild(), from);
      }
    }

    @Override
    protected boolean isWhiteSpace(PsiElement element) {
      return element instanceof PsiWhiteSpace;
    }
  }
}
