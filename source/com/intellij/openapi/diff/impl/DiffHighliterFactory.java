package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.ex.EditorHighlighter;

public interface DiffHighliterFactory {
  EditorHighlighter createHighlighter();
}
