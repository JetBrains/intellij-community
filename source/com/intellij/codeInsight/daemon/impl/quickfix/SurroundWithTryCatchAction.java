/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 19, 2002
 * Time: 8:28:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithTryCatchHandler;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class SurroundWithTryCatchAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.SurroundWithTryCatchAction");
  private PsiStatement myStatement;

  public SurroundWithTryCatchAction(PsiElement element) {
    myStatement = (PsiStatement)PsiTreeUtil.getParentOfType(element, new Class[]{PsiStatement.class}, false);
  }

  public String getText() {
    return "Surround with try/catch";
  }

  public String getFamilyName() {
    return "Surround with try/catch";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if(myStatement instanceof PsiExpressionStatement && ((PsiExpressionStatement) myStatement).getExpression() instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpr = (PsiMethodCallExpression) ((PsiExpressionStatement) myStatement).getExpression();
      PsiElement referenceName = callExpr.getMethodExpression().getReferenceNameElement();
      if (referenceName != null && referenceName.getText().equals("super")) return false;
    }
    return myStatement != null && myStatement.isValid();
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    LogicalPosition pos = new LogicalPosition(0, 0);
    editor.getCaretModel().moveToLogicalPosition(pos);
    TextRange range = null;

    if (myStatement.getParent() instanceof PsiForStatement) {
      PsiForStatement forStatement = (PsiForStatement)myStatement.getParent();
      if (myStatement.equals(forStatement.getInitialization()) || myStatement.equals(forStatement.getUpdate())) {
        myStatement = forStatement;
      }
    }

    try{
      SurroundWithTryCatchHandler handler = new SurroundWithTryCatchHandler();
      range = handler.surroundStatements(project, editor, myStatement.getParent(), new PsiElement[] {myStatement});
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    LogicalPosition pos1 = new LogicalPosition(line, col);
    editor.getCaretModel().moveToLogicalPosition(pos1);
    if (range != null) {
      int offset = range.getStartOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
