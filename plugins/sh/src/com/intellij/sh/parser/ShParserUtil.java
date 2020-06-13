// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShTokenTypes;
import gnu.trove.TObjectLongHashMap;

public class ShParserUtil extends GeneratedParserUtilBase {
  private static final Key<TObjectLongHashMap<String>> MODES_KEY = Key.create("MODES_KEY");

  private static TObjectLongHashMap<String> getParsingModes(PsiBuilder b) {
    TObjectLongHashMap<String> flags = b.getUserData(MODES_KEY);
    if (flags == null) b.putUserData(MODES_KEY, flags = new TObjectLongHashMap<>());
    return flags;
  }

  static boolean isModeOn(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, String mode) {
    return getParsingModes(b).get(mode) > 0;
  }

  static boolean withOn(PsiBuilder b, int level_, String mode, Parser parser) {
    return withImpl(b, level_, mode, true, parser, parser);
  }

  private static boolean withImpl(PsiBuilder b, int level_, String mode, boolean onOff, Parser whenOn, Parser whenOff) {
    TObjectLongHashMap<String> map = getParsingModes(b);
    long prev = map.get(mode);
    boolean change = ((prev & 1) == 0) == onOff;
    if (change) map.put(mode, prev << 1 | (onOff ? 1 : 0));
    boolean result = (change ? whenOn : whenOff).parse(b, level_);
    if (change) map.put(mode, prev);
    return result;
  }

  static boolean backslash(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(b, "\\\n");
  }

  static boolean notQuote(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    if (b.getTokenType() == ShTypes.OPEN_QUOTE || b.getTokenType() == ShTypes.CLOSE_QUOTE) return false;
    b.advanceLexer();
    return true;
  }

  static boolean differentBracketsWarning(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
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
      mark.done(ShTypes.SIMPLE_COMMAND_ELEMENT);
      return true;
    }
    return false;
  }

  static boolean functionNameKeywordsRemapped(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    IElementType type = b.getTokenType();
    if (ShTokenTypes.identifierKeywords.contains(type)) {
      b.remapCurrentToken(ShTypes.WORD);
      b.advanceLexer();
      return true;
    }
    return false;
  }
}
