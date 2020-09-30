/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.intellij.plugins.markdown.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.jetbrains.annotations.NotNull;

public class MarkdownParserAdapter implements PsiParser {
  @NotNull final MarkdownFlavourDescriptor myFlavour;

  public MarkdownParserAdapter() {
    this(MarkdownParserManager.FLAVOUR);
  }

  public MarkdownParserAdapter(@NotNull MarkdownFlavourDescriptor flavour) {
    myFlavour = flavour;
  }

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {

    PsiBuilder.Marker rootMarker = builder.mark();

    final org.intellij.markdown.ast.ASTNode parsedTree = MarkdownParserManager.parseContent(builder.getOriginalText(), myFlavour);

    new PsiBuilderFillingVisitor(builder).visitNode(parsedTree);
    assert builder.eof();

    rootMarker.done(root);

    return builder.getTreeBuilt();
  }
}
