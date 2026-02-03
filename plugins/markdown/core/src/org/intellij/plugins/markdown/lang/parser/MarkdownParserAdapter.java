// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.jetbrains.annotations.NotNull;

public class MarkdownParserAdapter implements PsiParser {
  final @NotNull MarkdownFlavourDescriptor myFlavour;

  public MarkdownParserAdapter() {
    this(MarkdownParserManager.FLAVOUR);
  }

  public MarkdownParserAdapter(@NotNull MarkdownFlavourDescriptor flavour) {
    myFlavour = flavour;
  }

  @Override
  public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    final var rootMarker = builder.mark();
    rootMarker.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    final var parsedTree = MarkdownParserManager.parseContent(builder.getOriginalText(), myFlavour);
    new PsiBuilderFillingVisitor(builder, true).visitNode(parsedTree);
    assert builder.eof();
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }
}
