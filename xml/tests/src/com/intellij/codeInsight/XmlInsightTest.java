// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;

public class XmlInsightTest extends LightPlatformTestCase {
  public void testDocumentDescriptor1() {
    XmlFile file = createFile("<root><a></a><b></b><a></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlElementDescriptor element = descriptor.getElementDescriptor(file.getDocument().getRootTag());
    assertNotNull(element);
    assertEquals("root", element.getName());

    element = descriptor.getElementDescriptor(file.getDocument().getRootTag().getSubTags()[0]);
    assertNotNull(element);
    assertEquals("a", element.getName());

    element = descriptor.getElementDescriptor(file.getDocument().getRootTag().getSubTags()[1]);
    assertNotNull(element);
    assertEquals("b", element.getName());

    element = descriptor.getElementDescriptor(file.getDocument().getRootTag().getSubTags()[2]);
    assertNotNull(element);
    assertEquals("a", element.getName());
  }

  public void testElementDescriptor1() {
    XmlFile file = createFile("<root><a></a><b></b><a></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlElementDescriptor element = descriptor.getElementDescriptor(rootTag);

    XmlElementDescriptor[] elements = element.getElementsDescriptors(rootTag);

    assertEquals(2, elements.length);
    assertEquals("a", elements[0].getName());
    assertEquals("b", elements[1].getName());
  }

  public void testElementDescriptor2() {
    XmlFile file = createFile("<root><a><b/></a><a><c/></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlElementDescriptor element = descriptor.getElementDescriptor(rootTag);
    element = element.getElementsDescriptors(rootTag)[0];

    XmlElementDescriptor[] elements = element.getElementsDescriptors(rootTag.getSubTags()[0]);

    assertEquals(2, elements.length);
    assertEquals("b", elements[0].getName());
    assertEquals("c", elements[1].getName());
  }

  public void testElementDescriptor3() {
    XmlFile file = createFile("<root><a><b/><c></c></a><a><c/></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlElementDescriptor element = descriptor.getElementDescriptor(rootTag);
    element = element.getElementsDescriptors(rootTag)[0];

    XmlElementDescriptor[] elements = element.getElementsDescriptors(rootTag.getSubTags()[0]);

    assertEquals(2, elements.length);
    assertEquals("b", elements[0].getName());
    //assertTrue(elements[0].getContentType() == XmlElementDescriptor.CONTENT_TYPE_EMPTY);

    assertEquals("c", elements[1].getName());
    //assertTrue(elements[1].getContentType() == XmlElementDescriptor.CONTENT_TYPE_CHILDREN);
  }

  public void testElementDescriptor4() {
    XmlFile file = createFile("<root><a attr2=''></a><a attr1=''></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlElementDescriptor element = descriptor.getElementDescriptor(rootTag);
    element = element.getElementsDescriptors(rootTag)[0];

    XmlAttributeDescriptor[] attributes = element.getAttributesDescriptors(rootTag);

    assertEquals(2, attributes.length);
    assertEquals("attr1", attributes[0].getName());
    assertEquals("attr2", attributes[1].getName());
  }

  public void testAttributeDescriptor1() {
    XmlFile file = createFile("<root><a attr1=''></a><a attr2='' attr1=''></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlElementDescriptor element = descriptor.getElementDescriptor(rootTag);
    element = element.getElementsDescriptors(rootTag)[0];

    XmlAttributeDescriptor[] attributes = element.getAttributesDescriptors(rootTag);

    assertEquals("attr1", attributes[0].getName());
    assertTrue(attributes[0].isRequired());

    assertEquals("attr2", attributes[1].getName());
    assertTrue(!attributes[1].isRequired());
  }

  public void testAttributeDescriptor2() {
    XmlFile file = createFile("<root><a c='' a=''></a></root>");
    XmlNSDescriptor descriptor = createDescriptor(file);

    XmlTag rootTag = file.getDocument().getRootTag();
    XmlElementDescriptor element = descriptor.getElementDescriptor(rootTag);
    element = element.getElementsDescriptors(rootTag)[0];

    XmlAttributeDescriptor[] attributes = element.getAttributesDescriptors(rootTag);

    assertEquals("c", attributes[0].getName());
    assertTrue(attributes[0].isRequired());

    assertEquals("a", attributes[1].getName());
    assertTrue(attributes[1].isRequired());
  }

  private XmlFile createFile(String text) {
    return (XmlFile)createFile("test.xml", text);
  }

  private static XmlNSDescriptor createDescriptor(XmlFile file) {
    return file.getDocument().getRootTagNSDescriptor();
  }

}
