
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;

public class GotoNextErrorHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler");

  public void invoke(Project project, Editor editor, PsiFile file) {
    int caretOffset = editor.getCaretModel().getOffset();
    gotoNextError(project, editor, file, caretOffset);
  }

  public boolean startInWriteAction() {
    return false;
  }

  private void gotoNextError(final Project project, final Editor editor, final PsiFile file, int caretOffset) {
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightInfo.WARNING, project);
    if (highlights == null || highlights.length == 0){
      showMessageWhenNoHighlights(project, file, editor);
      return;
    }

    int minOffset = Integer.MAX_VALUE;
    HighlightInfo minInfo = null;
    for(int i = 0; i < highlights.length; i++){
      HighlightInfo info = highlights[i];
      int startOffset = info.highlighter.getStartOffset();
      if (startOffset > caretOffset){
        if (startOffset < minOffset){
          minOffset = startOffset;
          minInfo = info;
        }
      }
    }

    if (minInfo != null){
      if (!navigateToError(project, editor, minInfo)){
        new Alarm().addRequest(
          new Runnable() {
            public void run() {
              CommandProcessor.getInstance().executeCommand(
                  project, new Runnable() {
                  public void run() {
                    ApplicationManager.getApplication().runWriteAction(
                      new Runnable() {
                        public void run() {
                          invoke(project, editor, file);
                        }
                      }
                    );
                  }
                },
                "",
                null
              );
            }
          },
          500
        );
      }
    }
    else{
      gotoNextError(project, editor, file, -1);
    }
  }

  static void showMessageWhenNoHighlights(Project project, PsiFile file, Editor editor) {
    DaemonCodeAnalyzerImpl codeHighlighter = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    String message;
    if (codeHighlighter.isErrorAnalyzingFinished(file)){
      message = "No errors found in this file";
    }
    else{
      message = "Error analysis is in progress";
    }
    HintManager.getInstance().showInformationHint(editor, message);
  }

  static boolean navigateToError(final Project project, final Editor editor, HighlightInfo info) {
    if (IS_OPTIMIZE_IMPORTS && info.type == HighlightInfoType.UNUSED_IMPORT && editor.getDocument().isWritable()){ // secret feature! :=)
      ApplicationManager.getApplication().runWriteAction(
        new Runnable() {
          public void run() {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            try{
              CodeStyleManager.getInstance(project).optimizeImports(file);
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        }
      );
      return false;
    }

    int oldOffset = editor.getCaretModel().getOffset();

    final int offset = getNavigationPositionFor(info);
    final int endOffset = info.highlighter.getEndOffset();

    final ScrollingModel scrollingModel = editor.getScrollingModel();
    if (offset != oldOffset) {
      ScrollType scrollType = offset > oldOffset ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().moveToOffset(offset);
      scrollingModel.scrollToCaret(scrollType);
    }

    scrollingModel.runActionOnScrollingFinished(
      new Runnable(){
        public void run() {
          scrollingModel.scrollTo(editor.offsetToLogicalPosition(endOffset), ScrollType.MAKE_VISIBLE);
          scrollingModel.scrollTo(editor.offsetToLogicalPosition(offset), ScrollType.MAKE_VISIBLE);
        }
      }
    );

    IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();

    return true;
  }

  private static final boolean IS_OPTIMIZE_IMPORTS = ApplicationManagerEx.getApplicationEx().isInternal()
                                                     && "Valentin".equalsIgnoreCase(System.getProperty("user.name"));

  private static int getNavigationPositionFor(HighlightInfo info) {
    int shift = info.isAfterEndOfLine ? +1 : info.navigationShift;
    return info.highlighter.getStartOffset() + shift;
  }
}