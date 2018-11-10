// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.PsiTestCase;

public class XmlModificationsTest extends PsiTestCase {
  private XmlElementFactory myFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFactory = XmlElementFactory.getInstance(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    myFactory = null;

    super.tearDown();
  }

  public void testAddAttribute1() {
    final XmlTag tag = myFactory.createTagFromText("<a></a>");
    tag.setAttribute("b", "");
    assertEquals("<a b=\"\"></a>", tag.getText());
    tag.setAttribute("c", "");
    assertEquals("<a b=\"\" c=\"\"></a>", tag.getText());
  }

  public void testAddAttribute2() {
    final XmlTag tag = myFactory.createTagFromText("<a/>");
    tag.setAttribute("b", "");
    assertEquals("<a b=\"\"/>", tag.getText());
    tag.setAttribute("c", "");
    assertEquals("<a b=\"\" c=\"\"/>", tag.getText());
  }

  public void testAddSubTag1() {
    final XmlTag tag = myFactory.createTagFromText("<a/>");
    tag.add(tag.createChildTag("b", "", null, false));
    assertEquals("<a><b/></a>", tag.getText());
  }

  public void testSetText() {
    final XmlTag tag = myFactory.createTagFromText("<a>foo</a>");
    XmlText[] elements = tag.getValue().getTextElements();
    assertEquals(1, elements.length);
    elements[0].setValue("");
    assertEquals("<a></a>", tag.getText());
  }
}
