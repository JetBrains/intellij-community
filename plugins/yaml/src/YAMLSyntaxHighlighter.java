// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.lexer.YAMLFlexLexer;

import java.util.HashMap;
import java.util.Map;

public final class YAMLSyntaxHighlighter extends SyntaxHighlighterBase implements YAMLTokenTypes {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  static {
    ATTRIBUTES.put(SCALAR_KEY, YAMLHighlighter.SCALAR_KEY);
    ATTRIBUTES.put(SCALAR_STRING, YAMLHighlighter.SCALAR_STRING);
    ATTRIBUTES.put(SCALAR_DSTRING, YAMLHighlighter.SCALAR_DSTRING);
    ATTRIBUTES.put(SCALAR_TEXT, YAMLHighlighter.SCALAR_TEXT);
    ATTRIBUTES.put(SCALAR_LIST, YAMLHighlighter.SCALAR_LIST);
    ATTRIBUTES.put(COMMENT, YAMLHighlighter.COMMENT);
    ATTRIBUTES.put(TEXT, YAMLHighlighter.TEXT);
    ATTRIBUTES.put(LBRACE, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(RBRACE, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(LBRACKET, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(RBRACKET, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(COMMA, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(QUESTION, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(COLON, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(AMPERSAND, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(DOCUMENT_MARKER, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(SEQUENCE_MARKER, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(ANCHOR, YAMLHighlighter.ANCHOR);
    ATTRIBUTES.put(ALIAS, YAMLHighlighter.ANCHOR);
    ATTRIBUTES.put(TAG, YAMLHighlighter.ANCHOR);
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new YAMLFlexLexer();
  }
}
