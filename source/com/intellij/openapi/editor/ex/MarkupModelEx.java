package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;

/**
 * @author max
 */ 
public interface MarkupModelEx extends MarkupModel {
  RangeHighlighter addPersistentLineHighlighter(int lineNumber, int layer, TextAttributes textAttributes);
}
