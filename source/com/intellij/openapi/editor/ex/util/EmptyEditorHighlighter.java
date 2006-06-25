package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;

public class EmptyEditorHighlighter implements EditorHighlighter, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter");

  private TextAttributes myAttributes;
  private int myTextLength = 0;
  private boolean myHasEditor = false;

  public EmptyEditorHighlighter(TextAttributes attributes) {
    myAttributes = attributes;
  }

  public void setAttributes(TextAttributes attributes) {
    myAttributes = attributes;
  }

  public void setText(CharSequence text) {
    myTextLength = text.length();
  }

  public void setEditor(HighlighterClient editor) {
    LOG.assertTrue(!myHasEditor, "Highlighters cannot be reused with different editors");
    myHasEditor = true;
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    setAttributes(scheme.getAttributes(HighlighterColors.TEXT));
  }

  public void documentChanged(DocumentEvent e) {
    myTextLength += e.getNewLength() - e.getOldLength();
  }

  public void beforeDocumentChange(DocumentEvent event) {}

  public int getPriority() {
    return 2;
  }

  public HighlighterIterator createIterator(int startOffset) {
    return new HighlighterIterator(){
      private int index = 0;

      public TextAttributes getTextAttributes() {
        return myAttributes;
      }

      public int getStart() {
        return 0;
      }

      public int getEnd() {
        return myTextLength;
      }

      public void advance() {
        index++;
      }

      public void retreat(){
        index--;
      }

      public boolean atEnd() {
        return index != 0;
      }

      public IElementType getTokenType(){
        return IElementType.find((short)0);
      }
    };
  }
}