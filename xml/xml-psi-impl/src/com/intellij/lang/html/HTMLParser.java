/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.lang.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class HTMLParser implements PsiParser {

  @Override
  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    parseWithoutBuildingTree(root, builder);
    return builder.getTreeBuilt();
  }

  public static void parseWithoutBuildingTree(IElementType root, PsiBuilder builder) {
    builder.enforceCommentTokens(TokenSet.EMPTY);
    final PsiBuilder.Marker file = builder.mark();
    new HtmlParsing(builder).parseDocument();
    file.done(root);
  }
}