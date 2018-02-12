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
package com.jetbrains.rest.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.RestElementTypes;
import com.jetbrains.rest.RestTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestParser implements PsiParser {
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    final PsiBuilder.Marker rootMarker = builder.mark();
    while (!builder.eof()) {
      IElementType type = builder.getTokenType();
      if (type == RestTokenTypes.EXPLISIT_MARKUP_START) {
        builder.advanceLexer();
        parseMarkup(builder);
      }
      else if (type == RestTokenTypes.REFERENCE_NAME || type == RestTokenTypes.SUBSTITUTION) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        marker.done(RestTokenTypes.REFERENCE_NAME);
      }
      else if (type == RestTokenTypes.TITLE) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        marker.done(RestTokenTypes.TITLE);
      }
      else if (type == RestTokenTypes.FIELD) {
        parseFieldList(builder);
      }
      else if (type == RestTokenTypes.INLINE_LINE) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        marker.done(RestElementTypes.INLINE_BLOCK);
      }
      else if (type == RestTokenTypes.ANONYMOUS_HYPERLINK) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        marker.done(RestElementTypes.REFERENCE_TARGET);
      }
      else if (type == RestTokenTypes.LINE) {
        parseLineText(builder, type);
      }
      else {
        builder.advanceLexer();
      }
    }
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }

  private static boolean parseLineText(PsiBuilder builder, IElementType type) {
    PsiBuilder.Marker marker = builder.mark();
    boolean gotLine = false;
    while (type == RestTokenTypes.LINE || type == RestTokenTypes.WHITESPACE) {
      builder.advanceLexer();
      type = builder.getTokenType();
      gotLine = true;
    }
    if (gotLine) {
      marker.done(RestElementTypes.LINE_TEXT);
    }
    else {
      marker.drop();
    }
    return gotLine;
  }

  private static void parseFieldList(PsiBuilder builder) {
    PsiBuilder.Marker listMarker = builder.mark();
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(RestTokenTypes.FIELD);
    if (parseLineText(builder, builder.getTokenType())) {
      listMarker.done(RestElementTypes.FIELD_LIST);
    }
    else {
      listMarker.drop();
    }
  }

  private static void parseMarkup(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    IElementType type = builder.getTokenType();
    if (type == RestTokenTypes.SUBSTITUTION) {
      builder.advanceLexer();
      marker.done(RestElementTypes.REFERENCE_TARGET);
      builder.advanceLexer();
      marker = builder.mark();
      type = builder.getTokenType();
    }
    if (type == RestTokenTypes.DIRECTIVE) {
      gotoNextWhiteSpaces(builder);
      if (builder.getTokenType() != RestTokenTypes.WHITESPACE) {
        builder.advanceLexer();
        marker.done(RestElementTypes.DIRECTIVE_BLOCK);
        return;
      }
      skipBlankLines(builder);
      final String tokenText = builder.getTokenText();
      if (builder.getTokenType() != RestTokenTypes.WHITESPACE ||
          (tokenText != null && StringUtil.getLineBreakCount(tokenText) == tokenText.length())) {
        marker.done(RestElementTypes.DIRECTIVE_BLOCK);
        return;
      }
      String white = builder.getTokenText();
      parseDirective(builder, white, marker);
    }
    else if (type == RestTokenTypes.FOOTNOTE || type == RestTokenTypes.CITATION ||
             type == RestTokenTypes.HYPERLINK || type == RestTokenTypes.ANONYMOUS_HYPERLINK) {
      builder.advanceLexer();
      marker.done(RestElementTypes.REFERENCE_TARGET);
    }
    else {
      builder.advanceLexer();
      marker.drop();
    }
  }

  private static void gotoNextWhiteSpaces(PsiBuilder builder) {
    while (!StringUtil.isEmptyOrSpaces(builder.getTokenText()) && !builder.eof() && (builder.getTokenType() != null)) {
      builder.advanceLexer();
    }
  }

  private static void skipBlankLines(PsiBuilder builder) {
    while ("\n".equals(builder.getTokenText()) && !builder.eof() && (builder.getTokenType() != null)) {
      builder.advanceLexer();
    }
  }

  private static void parseDirective(PsiBuilder builder, String white, PsiBuilder.Marker marker) {
    gotoNextWhiteSpaces(builder);
    if (builder.getTokenType() != RestTokenTypes.WHITESPACE) {
      builder.advanceLexer();
      marker.done(RestElementTypes.DIRECTIVE_BLOCK);
      return;
    }
    skipBlankLines(builder);
    if (white.equals(builder.getTokenText())) {
      builder.advanceLexer();
      parseDirective(builder, white, marker);
    }
    else {
      marker.done(RestElementTypes.DIRECTIVE_BLOCK);
    }
  }
}
