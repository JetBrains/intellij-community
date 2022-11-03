package com.jetbrains.python.editor;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyWhiteSpaceUtil {
  public static boolean needInsertBackslash(PsiFile file, int offset, boolean autoWrapInProgress) {
    if (offset > 0) {
      final PsiElement beforeCaret = file.findElementAt(offset - 1);
      if (beforeCaret instanceof PsiWhiteSpace && beforeCaret.getText().indexOf('\\') >= 0) {
        // we've got a backslash at EOL already, don't need another one
        return false;
      }
    }
    PsiElement atCaret = file.findElementAt(offset);
    if (atCaret == null) {
      return false;
    }
    ASTNode nodeAtCaret = atCaret.getNode();
    return needInsertBackslash(nodeAtCaret, autoWrapInProgress);
  }

  public static boolean needInsertBackslash(ASTNode nodeAtCaret, boolean autoWrapInProgress) {
    if (PsiTreeUtil.getParentOfType(nodeAtCaret.getPsi(), PyFStringFragment.class) != null) {
      return false;
    }

    PsiElement statementBefore = findStatementBeforeCaret(nodeAtCaret);
    PsiElement statementAfter = findStatementAfterCaret(nodeAtCaret);
    if (statementBefore != statementAfter) {  // Enter pressed at statement break
      return false;
    }
    if (statementBefore == null) {  // empty file
      return false;
    }

    if (PsiTreeUtil.hasErrorElements(statementBefore)) {
      if (!autoWrapInProgress) {
        // code is already bad, don't mess it up even further
        return false;
      }
      // if we're in middle of typing, it's expected that we will have error elements
    }

    final int offset = nodeAtCaret.getTextRange().getStartOffset();
    if (inFromImportParentheses(statementBefore, offset) || inWithItemsParentheses(statementBefore, offset)) {
      return false;
    }

    PsiElement wrappableBefore = findWrappable(nodeAtCaret, true);
    PsiElement wrappableAfter = findWrappable(nodeAtCaret, false);
    if (!(wrappableBefore instanceof PsiComment)) {
      while (wrappableBefore != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, PyEditorHandlerConfig.WRAPPABLE_CLASSES);
        if (next == null) {
          break;
        }
        wrappableBefore = next;
      }
    }
    if (!(wrappableAfter instanceof PsiComment)) {
      while (wrappableAfter != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, PyEditorHandlerConfig.WRAPPABLE_CLASSES);
        if (next == null) {
          break;
        }
        wrappableAfter = next;
      }
    }
    if (wrappableBefore instanceof PsiComment || wrappableAfter instanceof PsiComment) {
      return false;
    }
    if (wrappableAfter == null) {
      return !(wrappableBefore instanceof PyDecoratorList);
    }
    return wrappableBefore != wrappableAfter;
  }

  @Nullable
  private static PsiElement findWrappable(ASTNode nodeAtCaret, boolean before) {
    PsiElement wrappable = before
                                 ? findBeforeCaret(nodeAtCaret, PyEditorHandlerConfig.WRAPPABLE_CLASSES)
                                 : findAfterCaret(nodeAtCaret, PyEditorHandlerConfig.WRAPPABLE_CLASSES);
    if (wrappable == null) {
      PsiElement emptyTuple = before
                              ? findBeforeCaret(nodeAtCaret, PyTupleExpression.class)
                              : findAfterCaret(nodeAtCaret, PyTupleExpression.class);
      if (emptyTuple != null && emptyTuple.getNode().getFirstChildNode().getElementType() == PyTokenTypes.LPAR) {
        wrappable = emptyTuple;
      }
    }
    return wrappable;
  }

  @Nullable
  private static PsiElement findStatementBeforeCaret(ASTNode node) {
    return findBeforeCaret(node, PyStatement.class, PyStatementPart.class);
  }

  @Nullable
  private static PsiElement findStatementAfterCaret(ASTNode node) {
    return findAfterCaret(node, PyStatement.class, PyStatementPart.class);
  }

  private static PsiElement findBeforeCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
    while (atCaret != null) {
      atCaret = TreeUtil.prevLeaf(atCaret);
      if (atCaret != null && atCaret.getElementType() != TokenType.WHITE_SPACE) {
        return getNonStrictParentOfType(atCaret.getPsi(), classes);
      }
    }
    return null;
  }

  private static PsiElement findAfterCaret(ASTNode atCaret, Class<? extends PsiElement>... classes) {
    while (atCaret != null) {
      if (atCaret.getElementType() != TokenType.WHITE_SPACE) {
        return getNonStrictParentOfType(atCaret.getPsi(), classes);
      }
      atCaret = TreeUtil.nextLeaf(atCaret);
    }
    return null;
  }

  @Nullable
  private static <T extends PsiElement> T getNonStrictParentOfType(@NotNull PsiElement element, Class<? extends T> @NotNull ... classes) {
    PsiElement run = element;
    while (run != null) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(run)) return (T)run;
      }
      if (run instanceof PsiFile || run instanceof PyStatementList) break;
      run = run.getParent();
    }

    return null;
  }

  private static boolean inFromImportParentheses(PsiElement statement, int offset) {
    if (!(statement instanceof PyFromImportStatement)) {
      return false;
    }
    PyFromImportStatement fromImportStatement = (PyFromImportStatement)statement;
    PsiElement leftParen = fromImportStatement.getLeftParen();
    if (leftParen != null && offset >= leftParen.getTextRange().getEndOffset()) {
      return true;
    }
    return false;
  }

  private static boolean inWithItemsParentheses(@NotNull PsiElement statement, int offset) {
    if (!(statement instanceof PyWithStatement)) {
      return false;
    }

    final PsiElement leftParen = PyPsiUtils.getFirstChildOfType(statement, PyTokenTypes.LPAR);
    return leftParen != null && offset >= leftParen.getTextRange().getEndOffset();
  }
}
