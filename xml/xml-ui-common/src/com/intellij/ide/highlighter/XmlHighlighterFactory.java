// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;

public final class XmlHighlighterFactory {
  public static EditorHighlighter createXMLHighlighter(EditorColorsScheme settings) {
    return HighlighterFactory.createHighlighter(new XmlFileHighlighter(), settings);
  }
}