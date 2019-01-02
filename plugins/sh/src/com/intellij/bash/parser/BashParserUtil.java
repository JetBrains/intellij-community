package com.intellij.bash.parser;

import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;

public class BashParserUtil extends GeneratedParserUtilBase {
  public static boolean condOp(PsiBuilder builder_, @SuppressWarnings("UnusedParameters") int level) {
    return consumeTokenFast(builder_, BashTokenTypes.conditionalOperators);
  }
}
