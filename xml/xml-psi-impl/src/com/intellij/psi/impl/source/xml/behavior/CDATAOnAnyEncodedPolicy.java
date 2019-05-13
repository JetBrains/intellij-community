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
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.xml.util.XmlUtil;

public class CDATAOnAnyEncodedPolicy extends DefaultXmlPsiPolicy{
  @Override
  public ASTNode encodeXmlTextContents(String displayText, PsiElement text) {
    final ASTNode firstChild = text.getNode().getFirstChildNode();
    boolean textAlreadyHasCDATA = firstChild != null && firstChild.getElementType() == XmlElementType.XML_CDATA;
    if ((textAlreadyHasCDATA || XmlUtil.toCode(displayText)) && !displayText.isEmpty()) {
      final FileElement dummyParent = createCDATAElement(text.getManager(), SharedImplUtil.findCharTableByTree(text.getNode()), displayText);
      return dummyParent.getFirstChildNode();
    }
    else {
      return super.encodeXmlTextContents(displayText, text);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static FileElement createCDATAElement(final PsiManager manager, final CharTable charTableByTree, final String displayText) {
    final FileElement dummyParent = DummyHolderFactory.createHolder(manager, null, charTableByTree).getTreeElement();
    final CompositeElement cdata = ASTFactory.composite(XmlElementType.XML_CDATA);
    dummyParent.rawAddChildren(cdata);
    cdata.rawAddChildren(ASTFactory.leaf(XmlTokenType.XML_CDATA_START, "<![CDATA["));
    cdata.rawAddChildren(ASTFactory.leaf(XmlTokenType.XML_DATA_CHARACTERS, dummyParent.getCharTable().intern(displayText)));
    cdata.rawAddChildren(ASTFactory.leaf(XmlTokenType.XML_CDATA_END, "]]>"));
    dummyParent.acceptTree(new GeneratedMarkerVisitor());
    return dummyParent;
  }
}
