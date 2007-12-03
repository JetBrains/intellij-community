/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;

public class SimpleTreePatcher implements TreePatcher {
  public void insert(CompositeElement parent, TreeElement anchorBefore, OuterLanguageElement toInsert) {
    if(anchorBefore != null) {
      //[mike]
      //Nasty hack. Is used not to insert OuterLanguageElements before the first token of tag.
      //See GeneralJspParsingTest.testHtml6
      if (anchorBefore.getElementType() == XmlTokenType.XML_START_TAG_START) {
        anchorBefore = anchorBefore.getTreeParent();
      }


      TreeUtil.insertBefore(anchorBefore, (TreeElement)toInsert);
    }
    else TreeUtil.addChildren(parent, (TreeElement)toInsert);
  }

  public LeafElement split(LeafElement leaf, int offset, final CharTable table) {
    final CharSequence chars = leaf.getInternedText();
    final LeafElement leftPart = Factory.createLeafElement(leaf.getElementType(), chars, 0, offset, table);
    final LeafElement rightPart = Factory.createLeafElement(leaf.getElementType(), chars, offset, chars.length(), table);
    TreeUtil.insertAfter(leaf, leftPart);
    TreeUtil.insertAfter(leftPart, rightPart);
    TreeUtil.remove(leaf);
    return leftPart;
  }

}
