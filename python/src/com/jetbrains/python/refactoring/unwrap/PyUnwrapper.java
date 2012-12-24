package com.jetbrains.python.refactoring.unwrap;

import com.intellij.codeInsight.unwrap.AbstractUnwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyIfPartIfImpl;

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

  @Override
  public List<PsiElement> unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    List<PsiElement> res = super.unwrap(editor, element);
    for (PsiElement e : res) {
      CodeEditUtil.markToReformat(e.getNode(), true);
    }
    return res;
  }


  protected static class Context extends AbstractUnwrapper.AbstractContext {
    public void extractPart(PsiElement from) {
      if (from instanceof PyStatementWithElse) {
        extractFromConditionalBlock((PyStatementWithElse)from);
      }
      else if (from instanceof PyElsePart) {
        extractFromElseBlock((PyElsePart)from);
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
      if (statementList != null)
        extract(statementList.getFirstChild(), statementList.getLastChild(), from);
    }

    public void extractFromElseBlock(PyElsePart from) {
      PyStatementList body = from.getStatementList();
      if (body != null)
        extract(body.getFirstChild(), body.getLastChild(), from.getParent());
    }

    @Override
    protected boolean isWhiteSpace(PsiElement element) {
      return element instanceof PsiWhiteSpace;
    }
  }
}
