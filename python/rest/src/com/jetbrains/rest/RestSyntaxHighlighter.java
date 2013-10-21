/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.rest;

import com.google.common.collect.Maps;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.lexer.RestFlexLexer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * User : catherine
 */
public class RestSyntaxHighlighter extends SyntaxHighlighterBase implements RestTokenTypes {
  public static final TextAttributesKey REST_COMMENT = TextAttributesKey.createTextAttributesKey(
    "REST.LINE_COMMENT",
    DefaultLanguageHighlighterColors.LINE_COMMENT
  );

  public static final TextAttributesKey REST_SECTION_HEADER = TextAttributesKey.createTextAttributesKey(
    "REST.SECTION.HEADER",
    DefaultLanguageHighlighterColors.NUMBER
  );

  public static final TextAttributesKey REST_BOLD = TextAttributesKey.createTextAttributesKey(
    "REST.BOLD",
    DefaultLanguageHighlighterColors.IDENTIFIER
  );
  public static final TextAttributesKey REST_ITALIC = TextAttributesKey.createTextAttributesKey(
    "REST.ITALIC",
    DefaultLanguageHighlighterColors.IDENTIFIER
  );
  public static final TextAttributesKey REST_FIXED = TextAttributesKey.createTextAttributesKey(
    "REST.FIXED",
    DefaultLanguageHighlighterColors.IDENTIFIER
  );

  public static final TextAttributesKey REST_INTERPRETED = TextAttributesKey.createTextAttributesKey(
    "REST.INTERPRETED",
    DefaultLanguageHighlighterColors.IDENTIFIER
  );

  public static final TextAttributesKey REST_REF_NAME = TextAttributesKey.createTextAttributesKey(
    "REST.REF.NAME",
    DefaultLanguageHighlighterColors.STRING
  );

  public static final TextAttributesKey REST_EXPLICIT= TextAttributesKey.createTextAttributesKey(
    "REST.EXPLICIT",
    DefaultLanguageHighlighterColors.KEYWORD
  );
  public static final TextAttributesKey REST_FIELD = TextAttributesKey.createTextAttributesKey(
    "REST.FIELD",
    DefaultLanguageHighlighterColors.KEYWORD
  );

  public static final TextAttributesKey REST_INLINE = TextAttributesKey.createTextAttributesKey(
    "REST.INLINE",
    DefaultLanguageHighlighterColors.IDENTIFIER
  );
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = Maps.newHashMap();

  static {
    ATTRIBUTES.put(REFERENCE_NAME, REST_REF_NAME);
    ATTRIBUTES.put(DIRECT_HYPERLINK, REST_REF_NAME);
    ATTRIBUTES.put(TITLE, REST_SECTION_HEADER);
    ATTRIBUTES.put(TITLE_TEXT, REST_SECTION_HEADER);
    ATTRIBUTES.put(FOOTNOTE, REST_EXPLICIT);
    ATTRIBUTES.put(CITATION, REST_EXPLICIT);
    ATTRIBUTES.put(HYPERLINK, REST_REF_NAME);
    ATTRIBUTES.put(ANONYMOUS_HYPERLINK, REST_REF_NAME);
    ATTRIBUTES.put(DIRECTIVE, REST_EXPLICIT);
    ATTRIBUTES.put(CUSTOM_DIRECTIVE, REST_EXPLICIT);
    ATTRIBUTES.put(SUBSTITUTION, REST_EXPLICIT);
    ATTRIBUTES.put(COMMENT, REST_COMMENT);
    ATTRIBUTES.put(FIELD, REST_FIELD);
    ATTRIBUTES.put(BOLD, REST_BOLD);
    ATTRIBUTES.put(ITALIC, REST_ITALIC);
    ATTRIBUTES.put(FIXED, REST_FIXED);
    ATTRIBUTES.put(INTERPRETED, REST_INTERPRETED);
    ATTRIBUTES.put(INLINE_LINE, REST_INLINE);
    ATTRIBUTES.put(PYTHON_LINE, REST_INLINE);
    ATTRIBUTES.put(DJANGO_LINE, REST_INLINE);
  }


  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new RestFlexLexer();
  }
}
