
package com.intellij.openapi.editor.actions;

import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.AutoIndentLinesHandler;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ide.util.JavaUtil;

public class EmacsStyleIndentAction extends BaseCodeInsightAction{

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.actions.EmacsStyleIndentAction");

  protected CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected boolean isValidForFile(final Project project, final Editor editor, final PsiFile file) {
    return file.canContainJavaCode() || file instanceof XmlFile;
  }

  //----------------------------------------------------------------------
  private static class Handler implements CodeInsightActionHandler {

    public void invoke(final Project project, final Editor editor, final PsiFile file) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      if (!file.isWritable()){
        (editor.getDocument()).fireReadOnlyModificationAttempt();
        return;
      }

      final Document document = editor.getDocument();
      final int startOffset = editor.getCaretModel().getOffset();
      final int line = editor.offsetToLogicalPosition(startOffset).line;
      final int col = editor.getCaretModel().getLogicalPosition().column;
      final int lineStart = document.getLineStartOffset(line);
      final int initLineEnd = document.getLineEndOffset(line);
      try{
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        final int newPos = codeStyleManager.adjustLineIndent(file, lineStart);
        final int newCol = newPos - lineStart;
        final int lineInc = document.getLineEndOffset(line) - initLineEnd;
        if (newCol >= col + lineInc) {
          final LogicalPosition pos = new LogicalPosition(line, newCol);
          editor.getCaretModel().moveToLogicalPosition(pos);
          editor.getSelectionModel().removeSelection();
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}