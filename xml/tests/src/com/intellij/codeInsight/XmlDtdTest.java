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
package com.intellij.codeInsight;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.dtd.XmlElementDescriptorImpl;
import com.intellij.xml.impl.dtd.XmlNSDescriptorImpl;

/**
 * @author Mike
 */
public class XmlDtdTest extends LightPlatformTestCase {
  public void testDocumentDescriptor1() {
    XmlNSDescriptor NSDescriptor = createDescriptor("<!ELEMENT principals (#PCDATA)><!ELEMENT data-sources (#PCDATA)>");

    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag("principals"));
    assertNotNull(elementDescriptor);

    elementDescriptor = NSDescriptor.getElementDescriptor(tag("data-sources"));
    assertNotNull(elementDescriptor);

    elementDescriptor = NSDescriptor.getElementDescriptor(tag("xxx"));
    assertNull(elementDescriptor);
  }

  public void testElementDescriptor1() {
    XmlNSDescriptor NSDescriptor = createDescriptor("<!ELEMENT principals (#PCDATA)><!ELEMENT data-sources (#PCDATA)>");

    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag("principals"));
    assertEquals("principals", elementDescriptor.getName());

    elementDescriptor = NSDescriptor.getElementDescriptor(tag("data-sources"));
    assertEquals("data-sources", elementDescriptor.getName());
  }

  public void testElementDescriptor2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT principals (#PCDATA)><!ELEMENT data-sources ANY>" +
        "<!ELEMENT read-access (namespace-resource)><!ELEMENT group EMPTY>");

    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag("principals"));
    assertEquals(elementDescriptor.getContentType(), XmlElementDescriptor.CONTENT_TYPE_MIXED);

    elementDescriptor = NSDescriptor.getElementDescriptor(tag("data-sources"));
    assertEquals(elementDescriptor.getContentType(), XmlElementDescriptor.CONTENT_TYPE_ANY);

    elementDescriptor = NSDescriptor.getElementDescriptor(tag("read-access"));
    assertEquals(elementDescriptor.getContentType(), XmlElementDescriptor.CONTENT_TYPE_CHILDREN);

    elementDescriptor = NSDescriptor.getElementDescriptor(tag("group"));
    assertEquals(elementDescriptor.getContentType(), XmlElementDescriptor.CONTENT_TYPE_EMPTY);
  }

  public void testElementDescriptor3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT principals ANY><!ATTLIST principals path CDATA #IMPLIED smtp-host CDATA #REQUIRED>" +
        "<!ATTLIST principals address CDATA #IMPLIED>");

    final XmlTag tag = tag("principals");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("path", tag);
    assertNotNull(attributeDescriptor);

    attributeDescriptor = elementDescriptor.getAttributeDescriptor("xxx", tag);
    assertNull(attributeDescriptor);

    attributeDescriptor = elementDescriptor.getAttributeDescriptor("smtp-host", tag);
    assertNotNull(attributeDescriptor);

    attributeDescriptor = elementDescriptor.getAttributeDescriptor("address", tag);
    assertNotNull(attributeDescriptor);

    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
    assertEquals("path", descriptors[0].getName());
    assertEquals("smtp-host", descriptors[1].getName());
    assertEquals("address", descriptors[2].getName());
    assertEquals(3, descriptors.length);
  }

  public void testElementDescriptor4() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT orion-application (ejb-module*, persistence?, namespace-access)>" +
        "<!ELEMENT ejb-module ANY>" +
        "<!ELEMENT persistence ANY>" +
        "<!ELEMENT namespace-access ANY>");

    XmlTag documentTag = tag("orion-application");
    XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)NSDescriptor.getElementDescriptor(documentTag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(documentTag);

    assertEquals(3, elements.length);
    assertEquals("ejb-module", elements[0].getName());
    assertEquals("persistence", elements[1].getName());
    assertEquals("namespace-access", elements[2].getName());

    elements = elements[0].getElementsDescriptors(documentTag);
    assertEquals(4, elements.length);
    assertEquals("orion-application", elements[0].getName());
    assertEquals("ejb-module", elements[1].getName());
    assertEquals("persistence", elements[2].getName());
    assertEquals("namespace-access", elements[3].getName());
  }

  public void testAttributeDescriptor1() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT principals ANY><!ATTLIST principals path CDATA #IMPLIED>");

    final XmlTag tag = tag("principals");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("path", tag);
    assertEquals("path", attributeDescriptor.getName());
    assertTrue(!attributeDescriptor.isRequired());
    assertTrue(!attributeDescriptor.isFixed());
    assertTrue(!attributeDescriptor.isEnumerated());
    assertNull(attributeDescriptor.getDefaultValue());
  }

  public void testAttributeDescriptor2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT principals ANY><!ATTLIST principals path CDATA #IMPLIED>");

    final XmlTag tag = tag("principals");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("path", tag);
    assertTrue(!attributeDescriptor.isRequired());
    assertTrue(!attributeDescriptor.isFixed());
    assertTrue(!attributeDescriptor.isEnumerated());
    assertNull(attributeDescriptor.getDefaultValue());
  }

  public void testAttributeDescriptor3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT toc ANY> <!ATTLIST toc version CDATA #FIXED \"1.0\">");

    final XmlTag tag = tag("toc");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("version", tag);
    assertTrue(!attributeDescriptor.isRequired());
    assertTrue(attributeDescriptor.isFixed());
    assertTrue(!attributeDescriptor.isEnumerated());
    assertEquals("1.0", attributeDescriptor.getDefaultValue());
  }

  public void testAttributeDescriptor4() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT toc ANY> <!ATTLIST toc remote (true|false) \"false\">");

    final XmlTag tag = tag("toc");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("remote", tag);
    assertTrue(!attributeDescriptor.isRequired());
    assertTrue(!attributeDescriptor.isFixed());
    assertTrue(attributeDescriptor.isEnumerated());
    assertEquals("false", attributeDescriptor.getDefaultValue());

    String[] values = attributeDescriptor.getEnumeratedValues();
    assertEquals(2, values.length);
    assertEquals("true", values[0]);
    assertEquals("false", values[1]);
  }

  public void testAttributeDescriptor5() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ELEMENT toc ANY> <!ATTLIST toc remote (0|1|2) #REQUIRED>");

    final XmlTag tag = tag("toc");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("remote", tag);
    assertTrue(attributeDescriptor.isRequired());
    assertTrue(!attributeDescriptor.isFixed());
    assertTrue(attributeDescriptor.isEnumerated());
    assertNull(attributeDescriptor.getDefaultValue());

    String[] values = attributeDescriptor.getEnumeratedValues();
    assertEquals(3, values.length);
    assertEquals("0", values[0]);
    assertEquals("1", values[1]);
    assertEquals("2", values[2]);
  }

  public void testEntityDeclElement1() {
    final XmlNSDescriptor NSDescriptor = createDescriptor(
      "<!ENTITY % types \"fileset | patternset \"> <!ELEMENT project (target | taskdef | %types; | property )*> " +
      "<!ELEMENT target><!ELEMENT taskdef><!ELEMENT fileset><!ELEMENT patternset><!ELEMENT property>");

    XmlTag projectTag = tag("project");
    final XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(projectTag);

    final XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(projectTag);

    assertEquals(5, elements.length);
  }

  public void testEntityDecl1() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ENTITY % boolean \"(true|false|on|off|yes|no)\"> <!ELEMENT toc ANY> <!ATTLIST toc remote %boolean; \"false\"");

    final XmlTag tag = tag("toc");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("remote", tag);
    assertTrue(!attributeDescriptor.isRequired());
    assertTrue(!attributeDescriptor.isFixed());
    assertTrue(attributeDescriptor.isEnumerated());
    assertEquals("false", (attributeDescriptor.getDefaultValue()));

    String[] values = attributeDescriptor.getEnumeratedValues();
    assertEquals(6, values.length);
    assertEquals("true", values[0]);
    assertEquals("false", values[1]);
    assertEquals("on", values[2]);
    assertEquals("off", values[3]);
    assertEquals("yes", values[4]);
    assertEquals("no", values[5]);
  }

  public void testEntityDecl2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ENTITY % coreattrs \"id D #IMPLIED\"> <!ELEMENT a ANY> <!ATTLIST a %coreattrs; version CDATA #FIXED \"1.0\"");

    final XmlTag tag = tag("a");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    final XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);
    assertEquals(2, attributes.length);
    assertEquals("id", attributes[0].getName());
    assertEquals("version", attributes[1].getName());
  }

  public void testEntityDecl3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ENTITY % att1 \"id1 D #IMPLIED\"> <!ENTITY % att2 \"id2 D #IMPLIED\"> <!ELEMENT a ANY> <!ATTLIST a %att1; %att2; ");

    final XmlTag tag = tag("a");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    final XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);
    assertEquals(2, attributes.length);
    assertEquals("id1", attributes[0].getName());
    assertEquals("id2", attributes[1].getName());
  }

  public void testEntityDecl4() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ENTITY % boolean \'(true|false|on|off|yes|no)\'> <!ENTITY % bool \"%boolean;\">  <!ELEMENT toc ANY> <!ATTLIST toc remote %bool; \"false\"");

    final XmlTag tag = tag("toc");
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor("remote", tag);
    assertTrue(!attributeDescriptor.isRequired());
    assertTrue(!attributeDescriptor.isFixed());
    assertTrue(attributeDescriptor.isEnumerated());
    assertEquals("false", attributeDescriptor.getDefaultValue());

    String[] values = attributeDescriptor.getEnumeratedValues();
    assertEquals(6, values.length);
    assertEquals("true", values[0]);
    assertEquals("false", values[1]);
    assertEquals("on", values[2]);
    assertEquals("off", values[3]);
    assertEquals("yes", values[4]);
    assertEquals("no", values[5]);
  }

  public void testEntityDecl5() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<!ENTITY % boolean \"true | false\" > <!ELEMENT foo EMPTY> <!ATTLIST foo someBoolean (%boolean;) \"true\" someString CDATA #IMPLIED >");

    final XmlTag tag = tag("foo");
    final XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    final XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);

    assertEquals(2, attributes.length);
    assertEquals("someBoolean", attributes[0].getName());
    assertEquals("someString", attributes[1].getName());

    assertTrue(attributes[0].isEnumerated());
    assertEquals(2, attributes[0].getEnumeratedValues().length);
    assertEquals("true", attributes[0].getEnumeratedValues()[0]);
    assertEquals("false", attributes[0].getEnumeratedValues()[1]);
  }

  public void testEmbeddedDtd1() {
    XmlFile xmlFile = (XmlFile)createFile("test.xml",
      "<!DOCTYPE tv [ <!ELEMENT tv (date)*> <!ELEMENT date (#PCDATA)> ]> <tv></tv>");

    final XmlTag tag = xmlFile.getDocument().getRootTag();
    assertNotNull(tag);
    final XmlElementDescriptor desc = xmlFile.getDocument().getRootTagNSDescriptor().getElementDescriptor(tag);
    assertNotNull(desc);

    final XmlElementDescriptor[] elements = desc.getElementsDescriptors(tag);
    assertEquals(1, elements.length);
    assertEquals("date", elements[0].getName());
  }

  private static XmlNSDescriptor createDescriptor(String dtdText) {
    PsiFile dtdFile = createLightFile("test.dtd", dtdText);

    XmlNSDescriptorImpl descriptor = new XmlNSDescriptorImpl();
    descriptor.init(dtdFile);
    return descriptor;
  }

  private static XmlTag tag(String tagName) {
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("tag.xml", StdFileTypes.XML, "<" + tagName + "/>");
    return file.getDocument().getRootTag();
  }
}
