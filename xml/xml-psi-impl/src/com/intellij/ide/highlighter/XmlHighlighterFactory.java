// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;

public final class XmlHighlighterFactory {
  public static EditorHighlighter createXMLHighlighter(EditorColorsScheme settings) {
    return HighlighterFactory.createHighlighter(new XmlFileHighlighter(), settings);
  }
}