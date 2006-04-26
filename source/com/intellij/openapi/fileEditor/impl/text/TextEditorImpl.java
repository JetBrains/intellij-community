package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public final class TextEditorImpl extends UserDataHolderBase implements TextEditor{
  private final Project myProject;
  private final PropertyChangeSupport myChangeSupport;
  private final TextEditorComponent myComponent;
  private TextEditorBackgroundHighlighter myBackgroundHighlighter;

  TextEditorImpl(@NotNull final Project project, @NotNull final VirtualFile file) {
    myProject = project;
    myChangeSupport = new PropertyChangeSupport(this);
    myComponent = new TextEditorComponent(project, file, this);
  }

  void dispose(){
    myComponent.dispose();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public JComponent getPreferredFocusedComponent(){
    return getActiveEditor().getContentComponent();
  }

  @NotNull
  public Editor getEditor(){
    return getActiveEditor();
  }

  /**
   * @see TextEditorComponent#getEditor()
   */
  private Editor getActiveEditor() {
    return myComponent.getEditor();
  }

  @NotNull
  public String getName() {
    return "Text";
  }

  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    TextEditorState state = new TextEditorState();
    getStateImpl(myProject, getActiveEditor(), state, level);
    return state;
  }

  static void getStateImpl(final Project project, final Editor editor, @NotNull final TextEditorState state, @NotNull FileEditorStateLevel level){
    state.LINE = editor.getCaretModel().getLogicalPosition().line;
    state.COLUMN = editor.getCaretModel().getLogicalPosition().column;
    state.SELECTION_START = editor.getSelectionModel().getSelectionStart();
    state.SELECTION_END = editor.getSelectionModel().getSelectionEnd();

    // Save folding only on FULL level. It's very expensive to commit document on every
    // type (caused by undo).
    if(FileEditorStateLevel.FULL == level){
      // Folding
      if (project != null) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        state.FOLDING_STATE = CodeFoldingManager.getInstance(project).saveFoldingState(editor);
      }
      else {
        state.FOLDING_STATE = null;
      }
    }

    // Saving scrolling proportion on UNDO may cause undesirable results of undo action fails to perform since
    // scrolling proportion restored sligtly differs from what have been saved.
    state.VERTICAL_SCROLL_PROPORTION = level == FileEditorStateLevel.UNDO ? -1 : EditorUtil.calcVerticalScrollProportion(editor);
  }

  public void setState(@NotNull final FileEditorState state) {
    setStateImpl(myProject, getActiveEditor(), (TextEditorState)state);
  }

  static void setStateImpl(final Project project, final Editor editor, final TextEditorState state){
    LogicalPosition pos = new LogicalPosition(state.LINE, state.COLUMN);
    editor.getCaretModel().moveToLogicalPosition(pos);
    editor.getSelectionModel().removeSelection();
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    if (state.VERTICAL_SCROLL_PROPORTION != -1) {
      EditorUtil.setVerticalScrollProportion(editor, state.VERTICAL_SCROLL_PROPORTION);
    }

    final Document document = editor.getDocument();

    if (state.SELECTION_START == state.SELECTION_END) {
      editor.getSelectionModel().removeSelection();
    }
    else {
      int startOffset = Math.min(state.SELECTION_START, document.getTextLength());
      int endOffset = Math.min(state.SELECTION_END, document.getTextLength());
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
    ((EditorEx) editor).stopOptimizedScrolling();
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    // Folding
    if (project != null && state.FOLDING_STATE != null){
      PsiDocumentManager.getInstance(project).commitDocument(document);
      editor.getFoldingModel().runBatchFoldingOperation(
        new Runnable() {
          public void run() {
            CodeFoldingManager.getInstance(project).restoreFoldingState(editor, state.FOLDING_STATE);
          }
        }
      );
    }
  }

  public boolean isModified() {
    return myComponent.isModified();
  }

  public boolean isValid() {
    return myComponent.isEditorValid();
  }

  public void selectNotify() {
    myComponent.selectNotify();
  }

  public void deselectNotify() {
    myComponent.deselectNotify();
  }

  void firePropertyChange(final String propertyName, final Object oldValue, final Object newValue) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    if (myBackgroundHighlighter == null) {
      myBackgroundHighlighter = new TextEditorBackgroundHighlighter(myProject, getEditor());
    }
    return myBackgroundHighlighter;
  }

  public FileEditorLocation getCurrentLocation() {
    return new TextEditorLocation(getEditor().getCaretModel().getLogicalPosition(), this);
  }

  public StructureViewBuilder getStructureViewBuilder() {
    Document document = myComponent.getEditor().getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null || !file.isValid()) return null;
    return file.getFileType().getStructureViewBuilder(file, myProject);
  }

}
