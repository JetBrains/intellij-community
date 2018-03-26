// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  public ASTNode parse(@NotNull final IElementType root, @NotNull final PsiBuilder builder) {
    parseWithoutBuildingTree(root, builder, createHtmlParsing(builder));
    return builder.getTreeBuilt();
  }

  public static void parseWithoutBuildingTree(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    parseWithoutBuildingTree(root, builder, new HtmlParsing(builder));
  }

  private static void parseWithoutBuildingTree(@NotNull IElementType root, @NotNull PsiBuilder builder,
                                              @NotNull HtmlParsing htmlParsing) {
    builder.enforceCommentTokens(TokenSet.EMPTY);
    final PsiBuilder.Marker file = builder.mark();
    htmlParsing.parseDocument();
    file.done(root);
  }

  // to be able to manage what tags treated as single
  @NotNull
  protected HtmlParsing createHtmlParsing(@NotNull PsiBuilder builder) {
    return new HtmlParsing(builder);
  }
}