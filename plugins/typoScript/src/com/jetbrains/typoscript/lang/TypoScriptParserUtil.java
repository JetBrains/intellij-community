/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.jetbrains.typoscript.lang;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;


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
