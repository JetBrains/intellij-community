package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

public class CompareClipboardWithSelection extends BaseDiffAction {

  protected DiffRequest getDiffData(DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;
    Object editorData = dataContext.getData(DataConstants.EDITOR);
    Editor editor = editorData != null ?
                      (Editor)editorData :
                      FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return null;
    if (!editor.getSelectionModel().hasSelection()) return null;
    return new ClipboardSelectionContents(editor, project);
  }

  private static class ClipboardSelectionContents extends DiffRequest {
    private DiffContent[] myContents = null;
    private final Editor myEditor;

    public ClipboardSelectionContents(Editor editor, Project project) {
      super(project);
      myEditor = editor;
    }

    public String[] getContentTitles() {
      return new String[]{"Clipboard", "Selection from " + getContentTitle(getDocument())};
    }

    public DiffContent[] getContents() {
      if (myContents != null) return myContents;
      DiffContent clipboardContent = createClipboardContent();
      if (clipboardContent == null) return null;
      myContents = new DiffContent[2];
      myContents[0] = clipboardContent;

      SelectionModel selectionModel = myEditor.getSelectionModel();
      TextRange range = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      myContents[1] = new FragmentContent(DocumentContent.fromDocument(getProject(), getDocument()),
                                          range, getProject(), getDocumentFile(getDocument()));
      return myContents;
    }

    private Document getDocument() {
      return myEditor.getDocument();
    }

    public String getWindowTitle() {
      return "Clipboard vs " + getContentTitle(getDocument());
    }

    private DiffContent createClipboardContent() {
      Transferable content = CopyPasteManager.getInstance().getContents();
      String text;
      try {
        text = (String) (content.getTransferData(DataFlavor.stringFlavor));
      } catch (Exception e) {
        return null;
      }
      return text != null ? new SimpleContent(text) : null;
    }
  }
}
