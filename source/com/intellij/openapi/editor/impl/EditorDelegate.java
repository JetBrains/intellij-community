package com.intellij.openapi.editor.impl;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Alexey
 */
public class EditorDelegate implements EditorEx {
  private final DocumentRange myDocument;
  private final EditorImpl myDelegate;

  public EditorDelegate(DocumentRange document, final EditorImpl delegate) {
    myDocument = document;
    myDelegate = delegate;
  }

  public boolean isViewer() {
    return myDelegate.isViewer();
  }

  public boolean isRendererMode() {
    return myDelegate.isRendererMode();
  }

  public void setRendererMode(final boolean isRendererMode) {
    myDelegate.setRendererMode(isRendererMode);
  }

  public void setFile(final VirtualFile vFile) {
    myDelegate.setFile(vFile);
  }

  public SelectionModel getSelectionModel() {
    return myDelegate.getSelectionModel();
  }

  public MarkupModel getMarkupModel() {
    return myDelegate.getMarkupModel();
  }

  public FoldingModel getFoldingModel() {
    return myDelegate.getFoldingModel();
  }

  public CaretModel getCaretModel() {
    return new CaretDelegate(myDelegate.getCaretModel(), myDocument.getTextRange()) ;
  }

  public ScrollingModel getScrollingModel() {
    return myDelegate.getScrollingModel();
  }

  public EditorSettings getSettings() {
    return myDelegate.getSettings();
  }

  public void reinitSettings() {
    myDelegate.reinitSettings();
  }

  public void setFontSize(final int fontSize) {
    myDelegate.setFontSize(fontSize);
  }

  public void setHighlighter(final EditorHighlighter highlighter) {
    myDelegate.setHighlighter(highlighter);
  }

  public EditorHighlighter getHighlighter() {
    return myDelegate.getHighlighter();
  }

  public JComponent getContentComponent() {
    return myDelegate.getContentComponent();
  }

  public EditorGutterComponentEx getGutterComponentEx() {
    return myDelegate.getGutterComponentEx();
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  public void setInsertMode(final boolean mode) {
    myDelegate.setInsertMode(mode);
  }

  public boolean isInsertMode() {
    return myDelegate.isInsertMode();
  }

  public void setColumnMode(final boolean mode) {
    myDelegate.setColumnMode(mode);
  }

  public boolean isColumnMode() {
    return myDelegate.isColumnMode();
  }

  public VisualPosition xyToVisualPosition(final Point p) {
    return myDelegate.xyToVisualPosition(p);
  }

  public VisualPosition offsetToVisualPosition(final int offset) {
    return myDelegate.offsetToVisualPosition(offset);
  }

  public LogicalPosition offsetToLogicalPosition(final int offset) {
    return myDelegate.offsetToLogicalPosition(offset);
  }

  public LogicalPosition xyToLogicalPosition(final Point p) {
    return myDelegate.xyToLogicalPosition(p);
  }

  public Point logicalPositionToXY(final LogicalPosition pos) {
    return myDelegate.logicalPositionToXY(pos);
  }

  public Point visualPositionToXY(final VisualPosition visible) {
    return myDelegate.visualPositionToXY(visible);
  }

  public void repaint(final int startOffset, final int endOffset) {
    myDelegate.repaint(startOffset, endOffset);
  }

  public Document getDocument() {
    return myDocument;
  }

  public JComponent getComponent() {
    return myDelegate.getComponent();
  }

  public void addEditorMouseListener(final EditorMouseListener listener) {
    myDelegate.addEditorMouseListener(listener);
  }

  public void removeEditorMouseListener(final EditorMouseListener listener) {
    myDelegate.removeEditorMouseListener(listener);
  }

  public void addEditorMouseMotionListener(final EditorMouseMotionListener listener) {
    myDelegate.addEditorMouseMotionListener(listener);
  }

  public void removeEditorMouseMotionListener(final EditorMouseMotionListener listener) {
    myDelegate.removeEditorMouseMotionListener(listener);
  }

  public boolean isDisposed() {
    return myDelegate.isDisposed();
  }

  public void setBackgroundColor(final Color color) {
    myDelegate.setBackgroundColor(color);
  }

  public void resetBackgourndColor() {
    myDelegate.resetBackgourndColor();
  }

  public Color getBackroundColor() {
    return myDelegate.getBackroundColor();
  }

  public int getMaxWidthInRange(final int startOffset, final int endOffset) {
    return myDelegate.getMaxWidthInRange(startOffset, endOffset);
  }

  public int getLineHeight() {
    return myDelegate.getLineHeight();
  }

  public Dimension getContentSize() {
    return myDelegate.getContentSize();
  }

  public JScrollPane getScrollPane() {
    return myDelegate.getScrollPane();
  }

  public int logicalPositionToOffset(final LogicalPosition pos) {
    return myDelegate.logicalPositionToOffset(pos);
  }

  public void setLastColumnNumber(final int val) {
    myDelegate.setLastColumnNumber(val);
  }

  public int getLastColumnNumber() {
    return myDelegate.getLastColumnNumber();
  }

  public VisualPosition logicalToVisualPosition(final LogicalPosition logicalPos) {
    return myDelegate.logicalToVisualPosition(logicalPos);
  }

  public LogicalPosition visualToLogicalPosition(final VisualPosition visiblePos) {
    return myDelegate.visualToLogicalPosition(visiblePos);
  }

  public DataContext getDataContext() {
    return myDelegate.getDataContext();
  }

  public EditorMouseEventArea getMouseEventArea(final MouseEvent e) {
    return myDelegate.getMouseEventArea(e);
  }

  public void setCaretVisible(final boolean b) {
    myDelegate.setCaretVisible(b);
  }

  public void addFocusListener(final FocusChangeListener listener) {
    myDelegate.addFocusListener(listener);
  }

  public Project getProject() {
    return myDelegate.getProject();
  }

  public boolean isOneLineMode() {
    return myDelegate.isOneLineMode();
  }

  public boolean isEmbeddedIntoDialogWrapper() {
    return myDelegate.isEmbeddedIntoDialogWrapper();
  }

  public void setEmbeddedIntoDialogWrapper(final boolean b) {
    myDelegate.setEmbeddedIntoDialogWrapper(b);
  }

  public void setOneLineMode(final boolean isOneLineMode) {
    myDelegate.setOneLineMode(isOneLineMode);
  }

  public void stopOptimizedScrolling() {
    myDelegate.stopOptimizedScrolling();
  }

  public CopyProvider getCopyProvider() {
    return myDelegate.getCopyProvider();
  }

  public CutProvider getCutProvider() {
    return myDelegate.getCutProvider();
  }

  public PasteProvider getPasteProvider() {
    return myDelegate.getPasteProvider();
  }

  public DeleteProvider getDeleteProvider() {
    return myDelegate.getDeleteProvider();
  }

  public void setColorsScheme(final EditorColorsScheme scheme) {
    myDelegate.setColorsScheme(scheme);
  }

  public EditorColorsScheme getColorsScheme() {
    return myDelegate.getColorsScheme();
  }

  public void setVerticalScrollbarOrientation(final int type) {
    myDelegate.setVerticalScrollbarOrientation(type);
  }

  public void setVerticalScrollbarVisible(final boolean b) {
    myDelegate.setVerticalScrollbarVisible(b);
  }

  public void setHorizontalScrollbarVisible(final boolean b) {
    myDelegate.setHorizontalScrollbarVisible(b);
  }

  public boolean processKeyTyped(final KeyEvent e) {
    return myDelegate.processKeyTyped(e);
  }

  public EditorGutter getGutter() {
    return myDelegate.getGutter();
  }

  public <T> T getUserData(final Key<T> key) {
    return myDelegate.getUserData(key);
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    myDelegate.putUserData(key, value);
  }
}
