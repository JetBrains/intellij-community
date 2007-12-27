/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;

public class HtmlHighlighterFactory {
  public static EditorHighlighter createHTMLHighlighter(EditorColorsScheme settings){
    SyntaxHighlighter highlighter = new HtmlFileHighlighter();
    return HighlighterFactory.createHighlighter(highlighter, settings);
  }
}