package com.jetbrains.edu.learning;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class StudyAnswerPlaceholderExtendWordHandler implements ExtendWordSelectionHandler {

  @Nullable
  private static AnswerPlaceholder getAnswerPlaceholder(PsiElement e, int offset) {
    PsiFile file = e.getContainingFile();
    if (file == null) {
      return null;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(e.getProject(), virtualFile);
    if (taskFile == null) {
      return null;
    }
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      return null;
    }
    Editor editor = FileEditorManager.getInstance(e.getProject()).getSelectedTextEditor();
    return editor == null ? null : taskFile.getAnswerPlaceholder(document,
                                          editor.offsetToLogicalPosition(offset));
  }


  @Override
  public boolean canSelect(PsiElement e) {
    Editor editor = FileEditorManager.getInstance(e.getProject()).getSelectedTextEditor();
    if (editor == null) {
      return false;
    }
    return getAnswerPlaceholder(e, editor.getCaretModel().getOffset()) != null;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    AnswerPlaceholder placeholder = getAnswerPlaceholder(e, cursorOffset);
    assert placeholder != null;
    VirtualFile file = e.getContainingFile().getVirtualFile();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assert document != null;
    int startOffset = placeholder.getRealStartOffset(document);
    return Collections.singletonList(new TextRange(startOffset, startOffset + placeholder.getLength()));
  }
}
