package com.jetbrains.typoscript.lang;

import com.intellij.lang.PsiBuilder;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptParserUtil extends GeneratedParserUtilBase {

  public static boolean isAfterNewLine(PsiBuilder builder, int level) {
    return TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE == builder.rawLookup(-1);
  }
}
