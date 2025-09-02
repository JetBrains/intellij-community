// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.InjectedLanguageBlockBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlInjectedLanguageBlockBuilder extends InjectedLanguageBlockBuilder {
  private final XmlFormattingPolicy myXmlFormattingPolicy;

  public XmlInjectedLanguageBlockBuilder(final XmlFormattingPolicy formattingPolicy) {
    myXmlFormattingPolicy = formattingPolicy;
  }

  @Override
  public @NotNull Block createInjectedBlock(@NotNull ASTNode node,
                                            @NotNull Block originalBlock,
                                            Indent indent,
                                            int offset,
                                            TextRange range,
                                            @Nullable Language language) {
    return new AnotherLanguageBlockWrapper(node, myXmlFormattingPolicy, originalBlock, indent, offset, range);
  }

  @Override
  public Block createBlockBeforeInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
    return new XmlBlock(node, wrap, alignment, myXmlFormattingPolicy, indent, range);
  }

  @Override
  public Block createBlockAfterInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
    return new XmlBlock(node, wrap, alignment, myXmlFormattingPolicy, indent, range);
  }

  @Override
  public CodeStyleSettings getSettings() {
    return myXmlFormattingPolicy.getSettings();
  }

  @Override
  public boolean canProcessFragment(String text, final ASTNode injectionHost) {
    IElementType type = injectionHost.getElementType();
    if (type == XmlElementType.XML_TEXT) {
      return true;
    }
    else if (type == XmlElementType.XML_COMMENT) {   // <!--[if IE]>, <![endif]--> of conditional comments injection
      return true;
    }
    return text.isEmpty();
  }

}
