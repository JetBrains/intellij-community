package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.impl.SettingsImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author yole
 */
public class TextComponentEditor implements Editor {
  private Project myProject;
  private JTextComponent myTextComponent;
  private TextComponentDocument myDocument;
  private TextComponentCaretModel myCaretModel;
  private TextComponentSelectionModel mySelectionModel;
  private TextComponentScrollingModel myScrollingModel;
  private EditorSettings mySettings;

  public TextComponentEditor(final Project project, final JTextComponent textComponent) {
    myProject = project;
    myTextComponent = textComponent;
    myDocument = new TextComponentDocument(textComponent);
    myCaretModel = new TextComponentCaretModel(textComponent);
    mySelectionModel = new TextComponentSelectionModel(textComponent);
    myScrollingModel = new TextComponentScrollingModel(textComponent);
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  public boolean isViewer() {
    return !myTextComponent.isEditable();
  }

  @NotNull
  public JComponent getComponent() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public JComponent getContentComponent() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public FoldingModel getFoldingModel() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public ScrollingModel getScrollingModel() {
    return myScrollingModel;
  }

  @NotNull
  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  @NotNull
  public EditorSettings getSettings() {
    if (mySettings == null) {
      mySettings = new SettingsImpl(null);
    }
    return mySettings;
  }

  @NotNull
  public EditorColorsScheme getColorsScheme() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getLineHeight() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public Point logicalPositionToXY(@NotNull final LogicalPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int logicalPositionToOffset(@NotNull final LogicalPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull final LogicalPosition logicalPos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public Point visualPositionToXY(@NotNull final VisualPosition visible) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition visiblePos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(final int offset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull final Point p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull final Point p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void addEditorMouseListener(@NotNull final EditorMouseListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeEditorMouseListener(@NotNull final EditorMouseListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void addEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public boolean isDisposed() {
    return false;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public boolean isInsertMode() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public boolean isColumnMode() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public boolean isOneLineMode() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public EditorGutter getGutter() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public EditorMouseEventArea getMouseEventArea(@NotNull final MouseEvent e) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void setHeaderComponent(@Nullable final JComponent header) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public boolean hasHeaderComponent() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public <T> T getUserData(final Key<T> key) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    throw new UnsupportedOperationException("Not implemented");
  }
}