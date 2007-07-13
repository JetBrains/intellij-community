package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class TextEditorProvider implements FileEditorProvider {

  @NonNls private static final String TYPE_ID = "text-editor";
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.TextEditorProvider");
  private static final Key<TextEditor> TEXT_EDITOR_KEY = Key.create("textEditor");
  @NonNls private static final String LINE_ATTR = "line";
  @NonNls private static final String COLUMN_ATTR = "column";
  @NonNls private static final String SELECTION_START_ATTR = "selection-start";
  @NonNls private static final String SELECTION_END_ATTR = "selection-end";
  @NonNls private static final String VERTICAL_SCROLL_PROPORTION_ATTR = "vertical-scroll-proportion";
  @NonNls private static final String FOLDING_ELEMENT = "folding";

  public static TextEditorProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(TextEditorProvider.class);
  }

  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.isDirectory() || !file.isValid()) {
      return false;
    }
    FileType fileType = file.getFileType();
    return !fileType.isBinary() || fileType == StdFileTypes.CLASS;
  }

  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file) {
    LOG.assertTrue(accept(project, file));
    return new TextEditorImpl(project, file);
  }

  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  public FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    TextEditorState state = new TextEditorState();

    try {
      state.LINE = Integer.parseInt(element.getAttributeValue(LINE_ATTR));
      state.COLUMN = Integer.parseInt(element.getAttributeValue(COLUMN_ATTR));
      state.SELECTION_START = Integer.parseInt(element.getAttributeValue(SELECTION_START_ATTR));
      state.SELECTION_END = Integer.parseInt(element.getAttributeValue(SELECTION_END_ATTR));
      state.VERTICAL_SCROLL_PROPORTION = Float.parseFloat(element.getAttributeValue(VERTICAL_SCROLL_PROPORTION_ATTR));
    }
    catch (NumberFormatException ignored) {
    }

    // Foldings
    if (project != null) {
      Element child = element.getChild(FOLDING_ELEMENT);
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (child != null && document != null) {
        //PsiDocumentManager.getInstance(project).commitDocument(document);
        state.FOLDING_STATE = CodeFoldingManager.getInstance(project).readFoldingState(child, document);
      }
      else {
        state.FOLDING_STATE = null;
      }
    }

    return state;
  }

  public void writeState(@NotNull FileEditorState _state, @NotNull Project project, @NotNull Element element) {
    TextEditorState state = (TextEditorState)_state;

    element.setAttribute(LINE_ATTR, Integer.toString(state.LINE));
    element.setAttribute(COLUMN_ATTR, Integer.toString(state.COLUMN));
    element.setAttribute(SELECTION_START_ATTR, Integer.toString(state.SELECTION_START));
    element.setAttribute(SELECTION_END_ATTR, Integer.toString(state.SELECTION_END));
    element.setAttribute(VERTICAL_SCROLL_PROPORTION_ATTR, Float.toString(state.VERTICAL_SCROLL_PROPORTION));

    // Foldings
    if (state.FOLDING_STATE != null) {
      Element e = new Element(FOLDING_ELEMENT);
      try {
        CodeFoldingManager.getInstance(project).writeFoldingState(state.FOLDING_STATE, e);
      }
      catch (WriteExternalException e1) {
      }
      element.addContent(e);
    }
  }

  @NotNull
  public String getEditorTypeId() {
    return TYPE_ID;
  }

  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.NONE;
  }

  @NotNull public TextEditor getTextEditor(@NotNull Editor editor) {
    TextEditor textEditor = editor.getUserData(TEXT_EDITOR_KEY);
    if (textEditor == null) {
      textEditor = new DummyTextEditor(editor);
      putTextEditor(editor, textEditor);
    }

    return textEditor;
  }

  public static Document[] getDocuments(@NotNull FileEditor editor) {
    if (editor instanceof DocumentsEditor) {
      DocumentsEditor documentsEditor = (DocumentsEditor)editor;
      Document[] documents = documentsEditor.getDocuments();
      if (documents.length > 0) {
        return documents;
      }
      else {
        return null;
      }
    }

    if (editor instanceof TextEditor) {
      Document document = ((TextEditor)editor).getEditor().getDocument();
      return new Document[]{document};
    }

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = projects.length - 1; i >= 0; i--) {
      VirtualFile file = FileEditorManagerEx.getInstanceEx(projects[i]).getFile(editor);
      if (file != null) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          return new Document[]{document};
        }
      }
    }

    return null;
  }

  static void putTextEditor(Editor editor, TextEditor textEditor) {
    editor.putUserData(TEXT_EDITOR_KEY, textEditor);
  }

  private static final class DummyTextEditor extends UserDataHolderBase implements TextEditor {
    private final Editor myEditor;
    private TextEditorBackgroundHighlighter myBackgroundHighlighter;

    public DummyTextEditor(Editor editor) {
      myEditor = editor;
      myBackgroundHighlighter = myEditor.getProject() == null
                                ? null
                                : new TextEditorBackgroundHighlighter(myEditor.getProject(), myEditor);
    }

    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    @NotNull
    public JComponent getComponent() {
      return myEditor.getComponent();
    }

    public JComponent getPreferredFocusedComponent() {
      return myEditor.getContentComponent();
    }

    @NotNull
    public String getName() {
      return "Text";
    }

    public StructureViewBuilder getStructureViewBuilder() {
      VirtualFile file = FileDocumentManager.getInstance().getFile(myEditor.getDocument());
      final Project project = myEditor.getProject();
      LOG.assertTrue(project != null);
      return file.getFileType().getStructureViewBuilder(file, project);
    }

    @NotNull
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
      TextEditorState state = new TextEditorState();
      TextEditorImpl.getStateImpl(null, myEditor, state, level);
      return state;
    }

    public void setState(@NotNull FileEditorState state) {
      TextEditorImpl.setStateImpl(null, myEditor, (TextEditorState)state);
    }

    public boolean isModified() {
      return false;
    }

    public boolean isValid() {
      return true;
    }

    public void dispose() { }

    public void selectNotify() { }

    public void deselectNotify() { }

    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return myBackgroundHighlighter;
    }

    public FileEditorLocation getCurrentLocation() {
      return null;
    }

    public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
      return false;
    }

    public void navigateTo(@NotNull final Navigatable navigatable) {
    }
  }
}
