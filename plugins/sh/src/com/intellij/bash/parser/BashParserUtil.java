package com.intellij.bash.parser;

import com.intellij.bash.BashTypes;
import com.intellij.bash.lexer.BashLexer;
import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import gnu.trove.TObjectLongHashMap;

public class BashParserUtil extends GeneratedParserUtilBase {
  private static final Key<TObjectLongHashMap<String>> MODES_KEY = Key.create("MODES_KEY");

  private static TObjectLongHashMap<String> getParsingModes(PsiBuilder builder_) {
    TObjectLongHashMap<String> flags = builder_.getUserData(MODES_KEY);
    if (flags == null) builder_.putUserData(MODES_KEY, flags = new TObjectLongHashMap<>());
    return flags;
  }

  public static boolean isModeOn(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level, String mode) {
    return getParsingModes(builder_).get(mode) > 0;
  }

  public static boolean isModeOff(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level, String mode) {
    return getParsingModes(builder_).get(mode) == 0;
  }

  public static boolean withOn(PsiBuilder builder_, int level_, String mode, Parser parser) {
    return withImpl(builder_, level_, mode, true, parser, parser);
  }

  public static boolean withCleared(PsiBuilder builder_, int level_, String mode, Parser whenOn, Parser whenOff) {
    return withImpl(builder_, level_, mode, false, whenOn, whenOff);
  }

  private static boolean withImpl(PsiBuilder builder_, int level_, String mode, boolean onOff, Parser whenOn, Parser whenOff) {
    TObjectLongHashMap<String> map = getParsingModes(builder_);
    long prev = map.get(mode);
    boolean change = ((prev & 1) == 0) == onOff;
    if (change) map.put(mode, prev << 1 | (onOff ? 1 : 0));
    boolean result = (change ? whenOn : whenOff).parse(builder_, level_);
    if (change) map.put(mode, prev);
    return result;
  }

  public static boolean enterMode(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level, String mode) {
    TObjectLongHashMap<String> flags = getParsingModes(builder_);
    if (!flags.increment(mode)) flags.put(mode, 1);
    return true;
  }

  public static boolean exitMode(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level, String mode) {
    TObjectLongHashMap<String> flags = getParsingModes(builder_);
    long count = flags.get(mode);
    if (count == 1) {
      flags.remove(mode);
    }
    else if (count > 1) {
      flags.put(mode, count - 1);
    }
    else {
      builder_.error("Could not exit inactive '" + mode + "' mode at offset " + builder_.getCurrentOffset());
    }
    return true;
  }

  public static boolean condOp(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(builder_, BashTokenTypes.conditionalOperators);
  }

  public static boolean paramExpansionOp(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(builder_, BashTokenTypes.paramExpansionOperators);
  }

  public static boolean backslash(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(builder_, "\\\n");
  }

  public static boolean parseUntilSpace(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level, Parser parser) {
    PsiBuilder.Marker mark = builder_.mark();
    while (true) {
      if (!parser.parse(builder_, level) || BashLexer.whitespaceTokens.contains(builder_.rawLookup(0)) || builder_.eof()) {
        mark.drop();
        return true;
      }
    }
  }

  public static boolean keywordsRemapped(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level) {
    IElementType type = builder_.getTokenType();
    if (BashTokenTypes.identifierKeywords.contains(type)) {
      PsiBuilder.Marker mark = builder_.mark();
      builder_.remapCurrentToken(BashTypes.WORD);
      builder_.advanceLexer();
      mark.done(BashTypes.SIMPLE_COMMAND_ELEMENT);
      return true;
    }
    return false;
  }
}
