/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 10, 2002
 * Time: 5:54:59 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.markup.RangeHighlighter;

public interface RangeHighlighterEx extends RangeHighlighter {
  boolean isAfterEndOfLine();
  void setAfterEndOfLine(boolean val);
}
