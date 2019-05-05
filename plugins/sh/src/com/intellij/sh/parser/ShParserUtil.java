package com.intellij.sh.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShLexer;
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

  public static boolean isModeOff(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, String mode) {
    return getParsingModes(b).get(mode) == 0;
  }

  static boolean withOn(PsiBuilder b, int level_, String mode, Parser parser) {
    return withImpl(b, level_, mode, true, parser, parser);
  }

  public static boolean withCleared(PsiBuilder b, int level_, String mode, Parser whenOn, Parser whenOff) {
    return withImpl(b, level_, mode, false, whenOn, whenOff);
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

  public static boolean enterMode(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, String mode) {
    TObjectLongHashMap<String> flags = getParsingModes(b);
    if (!flags.increment(mode)) flags.put(mode, 1);
    return true;
  }

  public static boolean exitMode(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, String mode) {
    TObjectLongHashMap<String> flags = getParsingModes(b);
    long count = flags.get(mode);
    if (count == 1) {
      flags.remove(mode);
    }
    else if (count > 1) {
      flags.put(mode, count - 1);
    }
    else {
      b.error("Could not exit inactive '" + mode + "' mode at offset " + b.getCurrentOffset());
    }
    return true;
  }

  static boolean backslash(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(b, "\\\n");
  }

  static boolean notQuote(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    if (b.getTokenType() == ShTypes.QUOTE) return false;
    b.advanceLexer();
    return true;
  }

  static boolean addSpace(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    b.error("Add space");
    return true;
  }

  static boolean differentBracketsWarning(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level) {
    b.error("Expected similar close bracket");
    return true;
  }

  static boolean parseUntilSpace(PsiBuilder b, @SuppressWarnings("UnusedParameters") int level, Parser parser) {
    PsiBuilder.Marker mark = b.mark();
    while (true) {
      if (!parser.parse(b, level) || ShLexer.whitespaceTokens.contains(b.rawLookup(0)) || b.eof()) {
        mark.drop();
        return true;
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
}
