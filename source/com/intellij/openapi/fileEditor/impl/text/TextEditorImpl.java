package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.ide.util.treeView.smartTree.TreeModel;
import com.intellij.ide.structureView.StructureViewModel;
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

/**
 * @author Vladimir Kondratyev
 */
public final class TextEditorImpl extends UserDataHolderBase implements TextEditor{
  private final Project myProject;
  private final PropertyChangeSupport myChangeSupport;
  private final TextEditorComponent myComponent;
  private TextEditorBackgroundHighlighter myBackgroundHighlighter;

  TextEditorImpl(final Project project, final VirtualFile file) {
    if(project==null){
      throw new IllegalArgumentException("project cannot be null");
    }
    if(file==null){
      throw new IllegalArgumentException("file cannot be null");
    }
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

  /**
   * TODO[vova] remove this method as soon as splitting will not be a part of TextEditor
   * @return always not empty array
   */
  public Editor[] getAllEditors(){
    return myComponent.getAllEditors();
  }

  public Editor getEditor(){
    return getActiveEditor();
  }

  /**
   * @see TextEditorComponent#getEditor()
   */
  private Editor getActiveEditor() {
    return myComponent.getEditor();
  }

  public String getName() {
    return "Text";
  }

  public FileEditorState getState(FileEditorStateLevel level) {
    if (level == null) {
      throw new IllegalArgumentException("level cannot be null");
    }
    TextEditorState state = new TextEditorState();
    getStateImpl(myProject, getActiveEditor(), state, level);
    return state;
  }

  static void getStateImpl(final Project project, final Editor editor, final TextEditorState state, FileEditorStateLevel level){
    if (state == null) {
      throw new IllegalArgumentException("state cannot be null");
    }
    if (level == null) {
      throw new IllegalArgumentException("level cannot be null");
    }
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

      state.VERTICAL_SCROLL_PROPORTION = EditorUtil.calcVerticalScrollProportion(editor);
    }
    else {
      state.VERTICAL_SCROLL_PROPORTION = -1;
    }
  }

  public void setState(final FileEditorState state) {
    if (state == null){
      throw new IllegalArgumentException("state cannot be null");
    }
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

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
    myChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("listener cannot be null");
    }
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

  public StructureViewModel getStructureViewModel() {
    Document document = myComponent.getEditor().getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return null;
    return file.getFileType().getStructureViewModel(file, myProject);
  }
}
