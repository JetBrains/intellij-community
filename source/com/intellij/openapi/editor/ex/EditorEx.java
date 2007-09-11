package com.intellij.openapi.editor.ex;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;

public interface EditorEx extends Editor {
  @NonNls String PROP_INSERT_MODE = "insertMode";
  @NonNls String PROP_COLUMN_MODE = "columnMode";
  @NonNls String PROP_FONT_SIZE = "fontSize";
  Key<TextRange> LAST_PASTED_REGION = Key.create("LAST_PASTED_REGION");

  EditorGutterComponentEx getGutterComponentEx();

  EditorHighlighter getHighlighter();

  void setHighlighter(EditorHighlighter highlighter);

  void setColorsScheme(EditorColorsScheme scheme);

  void setInsertMode(boolean val);

  void setColumnMode(boolean val);

  void setLastColumnNumber(int val);

  int getLastColumnNumber();

  int VERTICAL_SCROLLBAR_LEFT = 0;
  int VERTICAL_SCROLLBAR_RIGHT = 1;

  void setVerticalScrollbarOrientation(int type);

  void setVerticalScrollbarVisible(boolean b);

  void setHorizontalScrollbarVisible(boolean b);

  CutProvider getCutProvider();

  CopyProvider getCopyProvider();

  PasteProvider getPasteProvider();

  DeleteProvider getDeleteProvider();

  void repaint(int startOffset, int endOffset);

  void reinitSettings();

  void addPropertyChangeListener(PropertyChangeListener listener);

  void removePropertyChangeListener(PropertyChangeListener listener);

  int getMaxWidthInRange(int startOffset, int endOffset);

  void stopOptimizedScrolling();

  void setCaretVisible(boolean b);

  void addFocusListener(FocusChangeListener listener);

  void setOneLineMode(boolean b);

  JScrollPane getScrollPane();

  boolean isRendererMode();

  void setRendererMode(boolean isRendererMode);

  void setFile(VirtualFile vFile);

  DataContext getDataContext();

  boolean processKeyTyped(KeyEvent e);

  void setFontSize(int fontSize);

  Color getBackroundColor();

  void setBackgroundColor(Color color);

  void resetBackgourndColor();

  Dimension getContentSize();

  boolean isEmbeddedIntoDialogWrapper();
  void setEmbeddedIntoDialogWrapper(boolean b);

  VirtualFile getVirtualFile();

  int calcColumnNumber(CharSequence text, int start, int offset, int tabSize);
}
