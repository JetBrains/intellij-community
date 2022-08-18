package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTryExceptAnnotator extends PyAnnotator {
  @Override
  public void visitPyTryExceptStatement(@NotNull PyTryExceptStatement node) {
    boolean haveStar = false;
    boolean haveNotStar = false;
    for (PyExceptPart exceptPart: node.getExceptParts()) {
      var star = PyPsiUtils.getFirstChildOfType(exceptPart, PyTokenTypes.MULT);
      if (star != null) {
        haveStar = true;
      }
      else {
        haveNotStar = true;
      }
      if (haveNotStar && haveStar) {
        var exceptKeyword = exceptPart.getFirstChild();
        int startOffset = exceptKeyword.getTextOffset();
        int endOffset = exceptKeyword.getTextRange().getEndOffset();
        if (star != null) {
          endOffset = star.getTextRange().getEndOffset();
        }
        var textRange = new TextRange(startOffset, endOffset);
        getHolder().newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.try.except.can.not.have.except.and.star.except"))
            .range(textRange).create();
        break;
      }
    }
  }

  @Nullable
  private static PyReferenceExpression tryGetExceptionGroupInExpression(@Nullable PsiElement exceptExpression) {
    if (exceptExpression instanceof PyReferenceExpression &&
        "ExceptionGroup".equals(((PyReferenceExpression)exceptExpression).getName())) {
      return (PyReferenceExpression)exceptExpression;
    }
    if (exceptExpression instanceof PyParenthesizedExpression) {
      return tryGetExceptionGroupInExpression(PyPsiUtils.flattenParens((PyParenthesizedExpression)exceptExpression));
    }
    if (exceptExpression instanceof PyTupleExpression) {
      for (PsiElement child: exceptExpression.getChildren()) {
        var result = tryGetExceptionGroupInExpression(child);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  @Override
  public void visitPyExceptBlock(@NotNull PyExceptPart node) {
    if (!node.isStar()) return;

    var exceptClass = node.getExceptClass();
    var exceptionGroup = tryGetExceptionGroupInExpression(exceptClass);
    if (exceptionGroup != null) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.exception.group.in.star.except")).range(exceptionGroup).create();
    }
  }

  @Override
  public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
    PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class, false, PyFunction.class);
    if (exceptPart != null && exceptPart.isStar()) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.continue.break.or.return.in.star.except")).create();
    }
  }

  private void checkForContinueAndReturn(@NotNull PsiElement node) {
    PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(node, PyExceptPart.class, false, PyLoopStatement.class);
    if (exceptPart != null && exceptPart.isStar()) {
      getHolder().newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.continue.break.or.return.in.star.except")).create();
    }
  }

  @Override
  public void visitPyContinueStatement(@NotNull PyContinueStatement node) {
    checkForContinueAndReturn(node);
  }

  @Override
  public void visitPyBreakStatement(@NotNull PyBreakStatement node) {
    checkForContinueAndReturn(node);
  }
}
