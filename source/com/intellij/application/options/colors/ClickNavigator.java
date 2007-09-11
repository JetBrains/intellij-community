/**
 * @author Yura Cangea
 */
package com.intellij.application.options.colors;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.ListScrollingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class ClickNavigator {
  private final JList myOptionsList;

  public ClickNavigator(JList optionsList) {
    myOptionsList = optionsList;
  }

  public static void setHandCursor(Editor view) {
    Cursor c = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    // XXX: Workaround, simply view.getContentComponent().setCursor(c) doesn't work
    if (view.getContentComponent().getCursor() != c) {
      view.getContentComponent().setCursor(c);
    }
  }

  public void addClickNavigatorToGeneralView(final Editor view) {
    view.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        setHandCursor(view);
      }
    });

    CaretListener listener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        setSelectedItem(HighlighterColors.TEXT.getExternalName(), true);
      }
    };
    view.getCaretModel().addCaretListener(listener);
  }

  private boolean setSelectedItem(String type, boolean select) {
    DefaultListModel model = (DefaultListModel)myOptionsList.getModel();

    for (int i = 0; i < model.size(); i++) {
      Object o = model.get(i);
      if (o instanceof EditorSchemeAttributeDescriptor) {
        if (type.equals(((EditorSchemeAttributeDescriptor)o).getType())) {
          if (select) {
            ListScrollingUtil.selectItem(myOptionsList, i);
          }
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isWhiteSpace(int offset, CharSequence text) {
    return offset <= 0 || offset >= text.length() ||
           text.charAt(offset) == ' ' || text.charAt(offset) == '\t' ||
           text.charAt(offset) == '\n' || text.charAt(offset) == '\r';
  }

  private static boolean highlightDataContainsOffset(HighlightData data, int offset) {
    return offset >= data.getStartOffset() && offset <= data.getEndOffset();
  }

  public void addClickNavigator(final Editor view,
                                final SyntaxHighlighter highlighter,
                                final HighlightData[] data,
                                final boolean isBackgroundImportant) {
    addMouseMotionListener(view, highlighter, data, isBackgroundImportant);

    CaretListener listener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        navigate(view, true, e.getNewPosition(), highlighter, data, isBackgroundImportant);
      }
    };
    view.getCaretModel().addCaretListener(listener);
  }

  private boolean selectItem(boolean select, HighlighterIterator itr, SyntaxHighlighter highlighter) {

    IElementType tokenType = itr.getTokenType();
    if (tokenType == null) return false;
    String type = highlightingTypeFromTokenType(tokenType, highlighter);
    return setSelectedItem(type, select);
  }

  public static String highlightingTypeFromTokenType(IElementType tokenType, SyntaxHighlighter highlighter) {
    TextAttributesKey[] highlights = highlighter.getTokenHighlights(tokenType);
    String s = null;
    for (TextAttributesKey highlight : highlights) {
      if (highlight != HighlighterColors.TEXT) {
        s = highlight.getExternalName();
        break;
      }
    }
    return s == null ? HighlighterColors.TEXT.getExternalName() : s;
  }

  private void addMouseMotionListener(final Editor view,
                                      final SyntaxHighlighter highlighter,
                                      final HighlightData[] data, final boolean isBackgroundImportant) {
    view.getContentComponent().addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        LogicalPosition pos = view.xyToLogicalPosition(new Point(e.getX(), e.getY()));
        navigate(view, false, pos, highlighter, data, isBackgroundImportant);
      }
    });
  }

  private void navigate(final Editor editor, boolean select,
                        LogicalPosition pos,
                        final SyntaxHighlighter highlighter,
                        final HighlightData[] data, final boolean isBackgroundImportant) {
    int offset = editor.logicalPositionToOffset(pos);

    if (!isBackgroundImportant && editor.offsetToLogicalPosition(offset).column != pos.column) {
      if (!select) {
        setCursor(editor, Cursor.TEXT_CURSOR);
        return;
      }
    }

    if (data != null) {
      for (HighlightData highlightData : data) {
        if (highlightDataContainsOffset(highlightData, editor.logicalPositionToOffset(pos))) {
          if (!select) setCursor(editor, Cursor.HAND_CURSOR);
          setSelectedItem(highlightData.getHighlightType(), select);
          return;
        }
      }
    }

    if (highlighter != null) {
      HighlighterIterator itr = ((EditorEx)editor).getHighlighter().createIterator(offset);
      boolean selection = selectItem(select, itr, highlighter);
      if (!select && selection) {
        setCursor(editor, Cursor.HAND_CURSOR);
      }
      else {
        setCursor(editor, Cursor.TEXT_CURSOR);
      }
    }
  }

  private static void setCursor(final Editor view, int type) {
    view.getContentComponent().setCursor(Cursor.getPredefinedCursor(type));
  }

}
