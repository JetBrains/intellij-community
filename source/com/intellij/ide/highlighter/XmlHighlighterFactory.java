/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;

public class XmlHighlighterFactory {
  public static EditorHighlighter createXMLHighlighter(EditorColorsScheme settings){
    return HighlighterFactory.createHighlighter(new XmlFileHighlighter(), settings);
  }
}