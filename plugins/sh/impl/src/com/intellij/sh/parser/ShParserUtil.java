// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.lang.WhitespaceSkippedCallback;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShTokenTypes;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

public class ShParserUtil extends GeneratedParserUtilBase {
  private static final Key<Object2LongMap<String>> MODES_KEY = Key.create("MODES_KEY");

  private static Object2LongMap<String> getParsingModes(PsiBuilder b) {
    Object2LongMap<String> flags = b.getUserData(MODES_KEY);
    if (flags == null) b.putUserData(MODES_KEY, flags = new Object2LongOpenHashMap<>());
    return flags;
  }

  static boolean isModeOn(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, String mode) {
    return getParsingModes(b).getLong(mode) > 0;
  }

  static boolean withOn(PsiBuilder b, int level_, String mode, Parser parser) {
    return withImpl(b, level_, mode, true, parser, parser);
  }

  private static boolean withImpl(PsiBuilder b, int level_, String mode, boolean onOff, Parser whenOn, Parser whenOff) {
    Object2LongMap<String> map = getParsingModes(b);
    long prev = map.getLong(mode);
    boolean change = ((prev & 1) == 0) == onOff;
    if (change) map.put(mode, prev << 1 | (onOff ? 1 : 0));
    boolean result = (change ? whenOn : whenOff).parse(b, level_);
    if (change) map.put(mode, prev);
    return result;
  }

  static boolean backslash(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(b, "\\\n");
  }

  static boolean notQuote(SyntaxTreeBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    if (b.getTokenType() == ShTypes.OPEN_QUOTE || b.getTokenType() == ShTypes.CLOSE_QUOTE) return false;
    b.advanceLexer();
    return true;
  }

  static boolean differentBracketsWarning(SyntaxTreeBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    b.error(ShBundle.message("sh.parser.expected.similar.close.bracket"));
    return true;
  }

  static boolean parseUntilSpace(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, Parser parser) {
    int startOffset = b.getCurrentOffset();
    PsiBuilder.Marker mark = b.mark();
    while (true) {
      if (!parser.parse(b, level) || ShTokenTypes.whitespaceTokens.contains(b.rawLookup(0)) || b.eof()) {
        mark.drop();
        return b.getCurrentOffset() > startOffset;
      }
    }
  }

  static boolean keywordsRemapped(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    IElementType type = b.getTokenType();
    if (ShTokenTypes.identifierKeywords.contains(type)) {
      PsiBuilder.Marker mark = b.mark();
      b.remapCurrentToken(ShTypes.WORD);
      b.advanceLexer();
      mark.done(ShTypes.LITERAL);
      return true;
    }
    return false;
  }

  static boolean arithmeticOperationsRemapped(PsiBuilder psiBuilder, @SuppressWarnings("UnusedParameters") int level) {
    IElementType type = psiBuilder.getTokenType();
    PsiBuilder.Marker marker = null;
    boolean[] isWhitespaceSkipped = new boolean[1];
    while (!isWhitespaceSkipped[0] && (ShTokenTypes.arithmeticOperationsForRemapping.contains(type) ||
                                       (marker != null && ShTokenTypes.numbers.contains(type)) ||
                                       type == ShTypes.WORD)) {
      if (marker == null) marker = psiBuilder.mark();
      psiBuilder.setWhitespaceSkippedCallback(new WhitespaceSkippedCallback() {
        @Override
        public void onSkip(IElementType type, int start, int end) {
          isWhitespaceSkipped[0] = true;
        }
      });
      psiBuilder.remapCurrentToken(ShTypes.WORD);
      psiBuilder.advanceLexer();
      type = psiBuilder.getTokenType();
    }

    psiBuilder.setWhitespaceSkippedCallback(null);
    if (marker != null) {
      marker.collapse(ShTypes.WORD);
      return true;
    }
    return false;
  }

  static boolean functionNameKeywordsRemapped(SyntaxTreeBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    IElementType type = b.getTokenType();
    if (ShTokenTypes.identifierKeywords.contains(type)) {
      b.remapCurrentToken(ShTypes.WORD);
      b.advanceLexer();
      return true;
    }
    return false;
  }
}
