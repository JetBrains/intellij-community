package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.PopupActionChooser;
import com.intellij.debugger.codeinsight.SurroundWithRuntimeCastHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public class SurroundWithHandler implements CodeInsightActionHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler");

  private static final String CHOOSER_TITLE = "Surround With";
  private static final PopupActionChooser ourStatementActionChooser = new PopupActionChooser(
    CHOOSER_TITLE,
    new String[]{
      "if",
      "if / else",
      "while",
      "do / while",
      "for",

      "try / catch",
      "try / finally",
      "try / catch / finally",
      "synchronized",
      "Runnable",

      "{ }",
    },
    new SurroundStatementsHandler[]{
      new SurroundWithIfHandler(),
      new SurroundWithIfElseHandler(),
      new SurroundWithWhileHandler(),
      new SurroundWithDoWhileHandler(),
      new SurroundWithForHandler(),

      new SurroundWithTryCatchHandler(),
      new SurroundWithTryFinallyHandler(),
      new SurroundWithTryCatchFinallyHandler(),
      new SurroundWithSynchronizedHandler(),
      new SurroundWithRunnableHandler(),

      new SurroundWithBlockHandler(),
    }
  );

  private static final PopupActionChooser ourExpressionActionChooser = new PopupActionChooser(
    CHOOSER_TITLE,
    new String[]{
      "(expr)",
      "((Type)expr)",
      "((RuntimeType)expr)",
      "!(expr)",
      "!(expr instanceof Type)",
      "if (expr) {...}",
      "if (expr) {...} else {...}",
    },
    new SurroundExpressionHandler[]{
      new SurroundWithParenthesesHandler(),
      new SurroundWithCastHandler(),
      new SurroundWithRuntimeCastHandler(),
      new SurroundWithNotHandler(),
      new SurroundWithNotInstanceofHandler(),
      new SurroundWithIfExpressionHandler(),
      new SurroundWithIfElseExpressionHandler(),
    }
  );

  public void invoke(final Project project, final Editor editor, PsiFile file){
    invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file, Object handler){
    if (!file.isWritable()){
      (editor.getDocument()).fireReadOnlyModificationAttempt();
      return;
    }

    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PopupActionChooser chooser = null;
    final PsiElement[] elements;
    final PsiElement container;
    final PsiExpression expr;

    expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null){
      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
      chooser = ourExpressionActionChooser;
      elements = null;
      container = null;
    }
    else{
      FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.statement");
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (elements != null && elements.length > 0) {
        container = elements[0].getParent();
        chooser = ourStatementActionChooser;
      }
      else{
        container = null;
      }
    }

    if (chooser == null) return;

    chooser.setShowNumbers(true);

    PopupActionChooser.Callback callback = new PopupActionChooser.Callback(){
      public boolean isApplicable(Object actionObject) {
        if (actionObject instanceof SurroundStatementsHandler){
          //SurroundStatementsHandler handler = (SurroundStatementsHandler)actionObject;
          return true;
        }
        else if (actionObject instanceof SurroundExpressionHandler){
          SurroundExpressionHandler handler = (SurroundExpressionHandler)actionObject;
          return handler.isApplicable(expr);
        }
        else{
          return false;
        }
      }

      public void execute(Object actionObject) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        int col = editor.getCaretModel().getLogicalPosition().column;
        int line = editor.getCaretModel().getLogicalPosition().line;
        LogicalPosition pos = new LogicalPosition(0, 0);
        editor.getCaretModel().moveToLogicalPosition(pos);
        TextRange range = null;
        try{
          if (actionObject instanceof SurroundStatementsHandler){
            SurroundStatementsHandler handler = (SurroundStatementsHandler)actionObject;
            range = handler.surroundStatements(project, editor, container, elements);
          }
          else if (actionObject instanceof SurroundExpressionHandler){
            SurroundExpressionHandler handler = (SurroundExpressionHandler)actionObject;
            range = handler.surroundExpression(project, editor, expr);
          }
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
    };

    if (handler == null){
      chooser.invoke(project, editor, callback);
    }
    else{
      callback.execute(handler);
    }
  }
}