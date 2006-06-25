package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.HighlighterClient;

public interface EditorHighlighter extends DocumentListener {
  HighlighterIterator createIterator(int startOffset);
  void setText(CharSequence text);
  void setEditor(HighlighterClient editor);
  void setColorScheme(EditorColorsScheme scheme);
}
