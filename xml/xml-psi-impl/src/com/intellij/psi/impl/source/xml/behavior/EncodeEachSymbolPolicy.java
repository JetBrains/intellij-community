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
package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.util.CharTable;

public class EncodeEachSymbolPolicy extends DefaultXmlPsiPolicy{
  @Override
  public ASTNode encodeXmlTextContents(String displayText, PsiElement text) {
    if(!toCode(displayText)) return super.encodeXmlTextContents(displayText, text);
    final FileElement dummyParent = DummyHolderFactory.createHolder(text.getManager(), null, SharedImplUtil.findCharTableByTree(text.getNode())).getTreeElement();
    int sectionStartOffset = 0;
    int offset = 0;
    while (offset < displayText.length()) {
      if (toCode(displayText.charAt(offset))) {
        final String plainSection = displayText.substring(sectionStartOffset, offset);
        if (!plainSection.isEmpty()) {
          dummyParent.rawAddChildren((TreeElement)super.encodeXmlTextContents(plainSection, text));
        }
        dummyParent.rawAddChildren(createCharEntity(displayText.charAt(offset), dummyParent.getCharTable()));
        sectionStartOffset = offset + 1;
      }
      offset++;
    }
    final String plainSection = displayText.substring(sectionStartOffset, offset);
    if (!plainSection.isEmpty()) {
      dummyParent.rawAddChildren((TreeElement)super.encodeXmlTextContents(plainSection, text));
    }

    dummyParent.acceptTree(new GeneratedMarkerVisitor());
    return dummyParent.getFirstChildNode();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static TreeElement createCharEntity(char ch, CharTable charTable) {
    switch (ch) {
      case '<':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&lt;");
      case '\'':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&apos;");
      case '"':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&quot;");
      case '>':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&gt;");
      case '&':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&amp;");
      case '\u00a0':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&nbsp;");

      default:
        final String charEncoding = "&#" + (int)ch + ";";
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, charTable.intern(charEncoding));
    }
  }

  private static boolean toCode(String str) {
    for (int i = 0; i < str.length(); i++) {
      final char ch = str.charAt(i);
      if ( toCode(ch)) return true;
    }
    return false;
  }

  private static boolean toCode(final char ch) {
    return "<&>\u00a0'\"".indexOf(ch) >= 0;
  }

}
