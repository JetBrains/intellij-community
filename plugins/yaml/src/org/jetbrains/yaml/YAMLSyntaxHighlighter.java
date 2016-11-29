/*
 * Copyright 2000-2008 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.yaml;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.lexer.YAMLFlexLexer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: oleg
 * @date: Feb 11, 2008
 */
public class YAMLSyntaxHighlighter extends SyntaxHighlighterBase implements YAMLTokenTypes {

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
    ATTRIBUTES.put(DOCUMENT_MARKER, YAMLHighlighter.SIGN);
    ATTRIBUTES.put(SEQUENCE_MARKER, YAMLHighlighter.SIGN);
  }


  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new YAMLFlexLexer();
  }
}
