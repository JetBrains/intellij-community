package com.intellij.openapi.editor.impl;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Alexey
 */
public class EditorDelegate implements EditorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorDelegate");
  private final DocumentRange myDocument;
  private final EditorImpl myDelegate;
  private final PsiFile myInjectedFile;
  private List<EditorMouseMotionListener> myMouseMotionListenerWrappers = new CopyOnWriteArrayList<EditorMouseMotionListener>();
  private List<EditorMouseListener> myMouseListenerWrappers = new CopyOnWriteArrayList<EditorMouseListener>();

  public EditorDelegate(DocumentRange document, final EditorImpl delegate, PsiFile injectedFile) {
    myDocument = document;
    myDelegate = delegate;
    myInjectedFile = injectedFile;
  }

  public PsiFile getInjectedFile() {
    return myInjectedFile;
  }
  public LogicalPosition parentToInjected(LogicalPosition pos) {
    int offsetInInjected = myDelegate.logicalPositionToOffset(pos) - myDocument.getTextRange().getStartOffset();
    return offsetToLogicalPosition(offsetInInjected);
  }
  public VisualPosition parentToInjected(VisualPosition pos) {
    LogicalPosition logical = parentToInjected(myDelegate.visualToLogicalPosition(pos));
    return logicalToVisualPosition(logical);
  }
  public LogicalPosition injectedToParent(LogicalPosition pos) {
    int offsetInParent = logicalPositionToOffset(pos) + myDocument.getTextRange().getStartOffset();
    return myDelegate.offsetToLogicalPosition(offsetInParent);
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

  @NotNull
  public SelectionModel getSelectionModel() {
    return new SelectionModelDelegate(myDelegate, myDocument,this);
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return new MarkupModelDelegate((EditorMarkupModelImpl)myDelegate.getMarkupModel(), myDocument,this);
  }

  @NotNull
  public FoldingModel getFoldingModel() {
    return myDelegate.getFoldingModel();
  }

  @NotNull
  public CaretModel getCaretModel() {
    return new CaretDelegate(myDelegate.getCaretModel(), myDocument.getTextRange(), this);
  }

  @NotNull
  public ScrollingModel getScrollingModel() {
    return myDelegate.getScrollingModel();
  }

  @NotNull
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

  @NotNull
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

  @NotNull
  public VisualPosition xyToVisualPosition(final Point p) {
    return logicalToVisualPosition(xyToLogicalPosition(p));
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(final int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset) {
    int lineStartOffset = myDocument.getLineStartOffset(myDocument.getLineNumber(offset));

    LogicalPosition windowPosition = myDelegate.offsetToLogicalPosition(myDocument.getTextRange().getStartOffset());
    LogicalPosition pos = myDelegate.offsetToLogicalPosition(offset + myDocument.getTextRange().getStartOffset());
    return new LogicalPosition(pos.line - windowPosition.line, offset - lineStartOffset);
  }

  @NotNull
  public LogicalPosition xyToLogicalPosition(final Point p) {
    LogicalPosition windowPosition = myDelegate.offsetToLogicalPosition(myDocument.getTextRange().getStartOffset());
    LogicalPosition pos = myDelegate.xyToLogicalPosition(p);
    int myOffset = logicalPositionToOffset(parentToInjected(pos));

    int lineStartOffset = myDocument.getLineStartOffset(myDocument.getLineNumber(myOffset));

    return new LogicalPosition(pos.line - windowPosition.line, myOffset - lineStartOffset);
  }

  @NotNull
  public Point logicalPositionToXY(final LogicalPosition pos) {
    return myDelegate.logicalPositionToXY(injectedToParent(pos));
  }

  @NotNull
  public Point visualPositionToXY(final VisualPosition pos) {
    return logicalPositionToXY(visualToLogicalPosition(pos));
  }

  public void repaint(final int startOffset, final int endOffset) {
    myDelegate.repaint(startOffset+myDocument.getTextRange().getStartOffset(), endOffset + myDocument.getTextRange().getEndOffset());
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public JComponent getComponent() {
    return myDelegate.getComponent();
  }

  public void addEditorMouseListener(final EditorMouseListener listener) {
    EditorMouseListener wrapper = new EditorMouseListener() {
      public void mousePressed(EditorMouseEvent e) {
        listener.mousePressed(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseClicked(EditorMouseEvent e) {
        listener.mouseClicked(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseReleased(EditorMouseEvent e) {
        listener.mouseReleased(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseEntered(EditorMouseEvent e) {
        listener.mouseEntered(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseExited(EditorMouseEvent e) {
        listener.mouseExited(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public int hashCode() {
        return listener.hashCode();
      }

      public boolean equals(Object obj) {
        return obj == listener || obj == this;
      }
    };
    myMouseListenerWrappers.add(wrapper);
    myDelegate.addEditorMouseListener(wrapper);
  }

  public void removeEditorMouseListener(final EditorMouseListener listener) {
    for (int i = 0; i < myMouseListenerWrappers.size(); i++) {
      EditorMouseListener wrapper = myMouseListenerWrappers.get(i);
      if (wrapper.equals(listener)) {
        myMouseListenerWrappers.remove(i);
        myDelegate.removeEditorMouseListener(wrapper);
        return;
      }
    }
    LOG.error("Listener not found");
  }

  public void addEditorMouseMotionListener(final EditorMouseMotionListener listener) {
    EditorMouseMotionListener wrapper = new EditorMouseMotionListener() {
      public void mouseMoved(EditorMouseEvent e) {
        listener.mouseMoved(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseDragged(EditorMouseEvent e) {
        listener.mouseDragged(new EditorMouseEvent(EditorDelegate.this, e.getMouseEvent(), e.getArea()));
      }

      public int hashCode() {
        return listener.hashCode();
      }

      public boolean equals(Object obj) {
        return obj == listener || obj == this;
      }
    };
    myMouseMotionListenerWrappers.add(wrapper);
    myDelegate.addEditorMouseMotionListener(wrapper);
  }

  public void removeEditorMouseMotionListener(final EditorMouseMotionListener listener) {
    for (int i = 0; i < myMouseMotionListenerWrappers.size(); i++) {
      EditorMouseMotionListener wrapper = myMouseMotionListenerWrappers.get(i);
      if (wrapper.equals(listener)) {
        myMouseMotionListenerWrappers.remove(i);
        myDelegate.removeEditorMouseMotionListener(wrapper);
        return;
      }
    }
    LOG.error("Listener not found");
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
    LogicalPosition windowPosition = myDelegate.offsetToLogicalPosition(myDocument.getTextRange().getStartOffset());
    LogicalPosition newPosition = new LogicalPosition(pos.line + windowPosition.line, pos.column + windowPosition.column);
    return myDelegate.logicalPositionToOffset(newPosition) - myDocument.getTextRange().getStartOffset();
  }

  public void setLastColumnNumber(final int val) {
    myDelegate.setLastColumnNumber(val);
  }

  public int getLastColumnNumber() {
    return myDelegate.getLastColumnNumber();
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(final LogicalPosition pos) {
    LogicalPosition windowPosition = myDelegate.offsetToLogicalPosition(myDocument.getTextRange().getStartOffset());
    LogicalPosition newPosition = new LogicalPosition(pos.line + windowPosition.line, pos.column + windowPosition.column);
    VisualPosition res = myDelegate.logicalToVisualPosition(newPosition);
    return new VisualPosition(res.line - windowPosition.line, res.column - windowPosition.column);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(final VisualPosition pos) {
    VisualPosition windowPosition = myDelegate.offsetToVisualPosition(myDocument.getTextRange().getStartOffset());
    VisualPosition newPosition = new VisualPosition(pos.line + windowPosition.line, pos.column + windowPosition.column);
    LogicalPosition res = myDelegate.visualToLogicalPosition(newPosition);
    return new LogicalPosition(res.line - windowPosition.line, res.column - windowPosition.column);
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

  @NotNull
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

  @NotNull
  public EditorGutter getGutter() {
    return myDelegate.getGutter();
  }

  public <T> T getUserData(final Key<T> key) {
    return myDelegate.getUserData(key);
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    myDelegate.putUserData(key, value);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EditorDelegate that = (EditorDelegate)o;

    if (myDelegate != null ? !myDelegate.equals(that.myDelegate) : that.myDelegate != null) return false;
    return Comparing.equal(myDocument.getTextRange(), that.myDocument.getTextRange());
  }

  public int hashCode() {
    return myDocument.getTextRange().getStartOffset();
  }

  public Editor getDelegate() {
    return myDelegate;
  }
}
