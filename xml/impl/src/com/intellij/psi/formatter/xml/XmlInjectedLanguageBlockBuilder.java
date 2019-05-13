/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
* @author nik
*/
public class XmlInjectedLanguageBlockBuilder extends InjectedLanguageBlockBuilder {
  private final XmlFormattingPolicy myXmlFormattingPolicy;

  public XmlInjectedLanguageBlockBuilder(final XmlFormattingPolicy formattingPolicy) {
    myXmlFormattingPolicy = formattingPolicy;
  }

  @NotNull
  @Override
  public Block createInjectedBlock(@NotNull ASTNode node,
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
      text = text.trim();
      text = text.replace("<![CDATA[", "");
      text = text.replace("]]>", "");
    }
    else if (type == XmlElementType.XML_COMMENT) {   // <!--[if IE]>, <![endif]--> of conditional comments injection
      return true;
    }

    return text.isEmpty();
  }

}
