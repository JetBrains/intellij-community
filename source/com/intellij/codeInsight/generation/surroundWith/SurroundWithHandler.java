package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;

public class SurroundWithHandler implements CodeInsightActionHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler");
  private static final String CHOOSER_TITLE = "Surround With";

  public void invoke(final Project project, final Editor editor, PsiFile file){
    invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file, Surrounder surrounder){
    if (!file.isWritable()){
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }

    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
    }

    if (element1 == null || element1.getLanguage() == null) return;
    final SurroundDescriptor[] surroundDescriptors = element1.getLanguage().getSurroundDescriptors();
    if (surroundDescriptors.length == 0) return;
    for (SurroundDescriptor descriptor : surroundDescriptors) {
      final PsiElement[] elements = descriptor.getElementsToSurround(file, startOffset, endOffset);
      if (elements.length > 0) {
        if (surrounder == null) {
          PopupActionChooser popupActionChooser = new PopupActionChooser(CHOOSER_TITLE);
          popupActionChooser.invoke(project, editor, descriptor.getSurrounders(), elements);
          return;
        }
        else {
          doSurround(project, editor, surrounder, elements);
        }
      }
    }
  }

  static void doSurround(final Project project, final Editor editor, final Surrounder surrounder, final PsiElement[] elements) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      int col = editor.getCaretModel().getLogicalPosition().column;
      int line = editor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      TextRange range = surrounder.surroundElements(project, editor, elements);
      if (TemplateManager.getInstance(project).getActiveTemplate(editor) == null) {
        LogicalPosition pos1 = new LogicalPosition(line, col);
        editor.getCaretModel().moveToLogicalPosition(pos1);
      }
      if (range != null) {
        int offset = range.getStartOffset();
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}