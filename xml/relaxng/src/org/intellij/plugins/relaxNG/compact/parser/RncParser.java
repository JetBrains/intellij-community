/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.xml.psi.XmlPsiBundle;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.jetbrains.annotations.NotNull;

public class RncParser implements PsiParser {

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, PsiBuilder builder) {
    final PsiBuilder.Marker fileMarker = builder.mark();
    final PsiBuilder.Marker docMarker = builder.mark();

    new PatternParsing(builder).parse();

    while (!builder.eof()) {
      builder.error(XmlPsiBundle.message("xml.parsing.unexpected.token"));
      builder.advanceLexer();
    }

    docMarker.done(RncElementTypes.DOCUMENT);
    fileMarker.done(root);
    return builder.getTreeBuilt();
  }
}
