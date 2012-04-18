package com.jetbrains.typoscript.lang;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptParserUtil extends GeneratedParserUtilBase {

  static boolean isAfterNewLine(PsiBuilder builder, int level) {
    return TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE == builder.rawLookup(-1);// &&
           //getLastVariantOffset(ErrorState.get(builder), builder.getCurrentOffset()) < builder.getCurrentOffset();
  }


  static boolean isObjectPathOnSameLine(PsiBuilder builder, int level) {
    final IElementType type = builder.rawLookup(0);
    if (TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE == type) {
      nextTokenIs(builder, TypoScriptTokenTypes.OBJECT_PATH_SEPARATOR);
      nextTokenIs(builder, TypoScriptTokenTypes.OBJECT_PATH_ENTITY);
      return false;
    }
    return TypoScriptGeneratedParser.object_path(builder, level + 1);
  }
}
