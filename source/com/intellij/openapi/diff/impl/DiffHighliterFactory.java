package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.ex.Highlighter;

public interface DiffHighliterFactory {
  Highlighter createHighlighter();
}
