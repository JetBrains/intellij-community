package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposeable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;

public class CurrentLineMarker implements CaretListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.CurrentLineMarker");
  private Editor myEditor;
  private RangeHighlighter myHighlighter = null;
  public static final int LAYER = HighlighterLayer.CARET_ROW + 1;

  public void attach(EditorSource editorSource) {
    if (myEditor != null) hide();
    myEditor = editorSource.getEditor();
    if (myEditor == null) return;
    final CaretModel caretModel = myEditor.getCaretModel();
    caretModel.addCaretListener(this);
    editorSource.addDisposable(new Disposeable() {
      public void dispose() {
        caretModel.removeCaretListener(CurrentLineMarker.this);
      }
    });
  }

  public void set() {
    if (myEditor == null) return;
    hide();
    myHighlighter = myEditor.getMarkupModel().addLineHighlighter(myEditor.getCaretModel().getLogicalPosition().line, LAYER, null);
    if (myHighlighter != null) {
      //myHighlighter.setGutterIconRenderer(new GutterIconRenderer() {
      //  public Icon getIcon() {
      //    return CURRENT_LINE_MARKER_ICON;
      //  }
      //});
    }
  }

  private boolean isHiden() { return myHighlighter == null; }

  void hide() {
    if (myHighlighter != null) {
      LOG.assertTrue(myEditor != null);
      myEditor.getMarkupModel().removeHighlighter(myHighlighter);
      myHighlighter = null;
    }
  }

  public void caretPositionChanged(CaretEvent e) {
    if (isHiden()) return;
    set();
  }
}