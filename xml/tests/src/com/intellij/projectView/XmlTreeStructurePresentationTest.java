// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.structureView.impl.xml.XmlTagTreeElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

public class XmlTreeStructurePresentationTest extends LightJavaCodeInsightTestCase {
  public void test() {
    String attributes = "attr1=\"value1\" arre2=\"value2\"";
    final String tagPresentation = "a " + attributes;
    final String tagText = "<" + tagPresentation + "/>";
    XmlTag xmlTag = XmlElementFactory.getInstance(getProject()).createTagFromText(tagText);
    final XmlTagTreeElement treeElement = new XmlTagTreeElement(xmlTag);
    assertEquals(xmlTag.getName(), treeElement.getPresentableText());
    assertEquals(attributes, treeElement.getLocationString());
  }
}
