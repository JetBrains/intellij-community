package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentListener;

public interface Highlighter extends DocumentListener {
  HighlighterIterator createIterator(int startOffset);
  void setText(CharSequence text);
  void setEditor(Editor editor);
  void setColorScheme(EditorColorsScheme scheme);
}
