package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

public class EndHandler extends EditorActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.EndHandler");

  private EditorActionHandler myOriginalHandler;

  public EndHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(final Editor editor, DataContext dataContext) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.SMART_END_ACTION){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    final Project project = (Project)DataManager.getInstance().getDataContext(editor.getComponent()).getData(DataConstants.PROJECT);
    if (project == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }
    Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null || !document.isWritable()){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    final CaretModel caretModel = editor.getCaretModel();
    final int caretOffset = caretModel.getOffset();
    CharSequence chars = editor.getDocument().getCharsSequence();
    int length = editor.getDocument().getTextLength();
    if (caretOffset < length){
      final int offset1 = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t");
      if (offset1 < 0 || chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r'){
        int offset2 = CharArrayUtil.shiftForward(chars, offset1 + 1, " \t");
        boolean isEmptyLine = offset2 >= length || chars.charAt(offset2) == '\n' || chars.charAt(offset2) == '\r';
        if (isEmptyLine){
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                final PsiFile fileCopy = (PsiFile)file.copy();
                CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
                int newOffset = styleManager.adjustLineIndent(fileCopy, caretOffset);

                String newChars = fileCopy.getText();
                int lineStart = offset1 + 1;
                int tabSize = editor.getSettings().getTabSize(project);
                int col = EditorUtil.calcColumnNumber(editor, newChars, lineStart, newOffset, tabSize);
                int line = caretModel.getLogicalPosition().line;
                caretModel.moveToLogicalPosition(new LogicalPosition(line, col));

                if (caretModel.getLogicalPosition().column != col){
                  final String indentString = newChars.substring(lineStart, newOffset);
                  editor.getSelectionModel().removeSelection();
                  EditorModificationUtil.insertStringAtCaret(editor, indentString);
                }

                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                editor.getSelectionModel().removeSelection();
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
          return;
        }
      }
    }

    if (myOriginalHandler != null){
      myOriginalHandler.execute(editor, dataContext);
    }
  }
}