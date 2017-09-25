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

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author Mike
 */
public class XmlSchemaTest extends LightCodeInsightTestCase {
  private XmlTag SHIP_TO;
  private XmlTag UNKNOWN_TAG;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SHIP_TO = XmlTestUtil.tag("shipTo", getProject());
    UNKNOWN_TAG = XmlTestUtil.tag("xxx", getProject());
  }

  public void testDocumentDescriptor1() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:element name=\"comment\" type=\"xsd:string\"/>" +
        "</xsd:schema>");

    assertNotNull(NSDescriptor);
    assertNotNull(NSDescriptor.getElementDescriptor(XmlTestUtil.tag("purchaseOrder", getProject())));
    assertNotNull(NSDescriptor.getElementDescriptor(XmlTestUtil.tag("comment", getProject())));
    assertNull(NSDescriptor.getElementDescriptor(UNKNOWN_TAG));
  }

  public void testElementDescriptor1() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:element name=\"comment\" type=\"xsd:string\"/>" +
        "</xsd:schema>");

    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(XmlTestUtil.tag("purchaseOrder", getProject()));
    assertEquals("purchaseOrder", elementDescriptor.getName());

    elementDescriptor = NSDescriptor.getElementDescriptor(XmlTestUtil.tag("comment", getProject()));
    assertEquals("comment", elementDescriptor.getName());
  }

  public void testElementDescriptor2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"comment\" type=\"xsd:string\"/>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("comment", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(0, elements.length);

    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
    assertEquals(0, descriptors.length);

    assertEquals(elementDescriptor.getContentType(), XmlElementDescriptor.CONTENT_TYPE_MIXED);
  }

  public void testElementDescriptor3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element name=\"shipTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"billTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"items\" type=\"Items\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(3, elements.length);
    assertEquals("shipTo", elements[0].getName());
    assertEquals("billTo", elements[1].getName());
    assertEquals("items", elements[2].getName());

    assertEquals("shipTo", elementDescriptor.getElementDescriptor(SHIP_TO, null).getName());
    assertNull(elementDescriptor.getElementDescriptor(UNKNOWN_TAG, null));
  }

  public void testElementDescriptor4() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:attribute name=\"orderDate\" type=\"xsd:date\"/>" +
        "   <xsd:attribute name=\"name\" type=\"xsd:string\"/>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);

    assertEquals(2, attributes.length);
    assertEquals("orderDate", attributes[0].getName());
    assertEquals("name", attributes[1].getName());

    assertEquals("name", elementDescriptor.getAttributeDescriptor("name", tag).getName());

    assertNull(elementDescriptor.getAttributeDescriptor("xxx", tag));
  }

  public void testElementDescriptor5() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element name=\"shipTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"billTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"items\" type=\"Items\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "<xsd:complexType name=\"USAddress\">" +
        "  <xsd:sequence>" +
        "    <xsd:element name=\"name\" type=\"xsd:string\"/>" +
        "    <xsd:element name=\"street\" type=\"xsd:string\"/>" +
        "    <xsd:element name=\"city\" type=\"xsd:string\"/>" +
        "    <xsd:element name=\"state\" type=\"xsd:string\"/>" +
        "    <xsd:element name=\"zip\" type=\"xsd:decimal\"/>" +
        "  </xsd:sequence>" +
        "  <xsd:attribute name=\"country\" type=\"xsd:NMTOKEN\" fixed=\"US\"/>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag).getElementDescriptor(SHIP_TO, null);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(5, elements.length);
    assertEquals("name", elements[0].getName());
    assertEquals("street", elements[1].getName());
    assertEquals("city", elements[2].getName());
    assertEquals("state", elements[3].getName());
    assertEquals("zip", elements[4].getName());

    final XmlTag context = tag.findFirstSubTag(elements[2].getName());
    assertEquals(0, elements[2].getElementsDescriptors(context).length);

    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(context);
    assertEquals(1, attributes.length);
    assertEquals("country", attributes[0].getName());
  }

  public void testElementDescriptor6() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element name=\"shipTo\">" +
        "       <xsd:complexType name=\"USAddress\">" +
        "         <xsd:sequence>" +
        "           <xsd:element name=\"name\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"street\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"city\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"state\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"zip\" type=\"xsd:decimal\"/>" +
        "         </xsd:sequence>" +
        "         <xsd:attribute name=\"country\" type=\"xsd:NMTOKEN\" fixed=\"US\"/>" +
        "       </xsd:complexType>" +
        "     </xsd:element>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag).getElementDescriptor(SHIP_TO, null);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(5, elements.length);
    assertEquals("name", elements[0].getName());
    assertEquals("street", elements[1].getName());
    assertEquals("city", elements[2].getName());
    assertEquals("state", elements[3].getName());
    assertEquals("zip", elements[4].getName());

    final XmlTag context = tag.findFirstSubTag(elements[2].getName());
    assertEquals(0, elements[2].getElementsDescriptors(context).length);

    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(context);
    assertEquals(1, attributes.length);
    assertEquals("country", attributes[0].getName());
  }

  public void testElementDescriptor7() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\">" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element name=\"shipTo\">" +
        "       <xsd:complexType name=\"USAddress\">" +
        "         <xsd:sequence>" +
        "           <xsd:element name=\"name\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"street\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"city\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"state\" type=\"xsd:string\"/>" +
        "           <xsd:element name=\"zip\" type=\"xsd:decimal\"/>" +
        "         </xsd:sequence>" +
        "         <xsd:attribute name=\"country\" type=\"xsd:NMTOKEN\" fixed=\"US\"/>" +
        "       </xsd:complexType>" +
        "     </xsd:element>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:element>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);
    assertEquals(0, attributes.length);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(1, elements.length);
    assertEquals("shipTo", elements[0].getName());
  }

  public void testElementDescriptor8() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element ref=\"items\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "<xsd:element name=\"items\" type=\"xsd:string\"/>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(1, elements.length);
    assertEquals("items", elements[0].getName());

    assertEquals("items", elementDescriptor.getElementDescriptor(XmlTestUtil.tag("items", getProject()), null).getName());
    assertNull(elementDescriptor.getElementDescriptor(UNKNOWN_TAG, null));
  }

  public void testElementDescriptor9() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:group ref=\"ddd:mainBookElements\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "<xsd:group name=\"mainBookElements\">" +
        "   <xsd:sequence>" +
        "       <xsd:element name=\"title\" type=\"nameType\"/>" +
        "       <xsd:element name=\"author\" type=\"nameType\"/>" +
        "   </xsd:sequence>" +
        "</xsd:group>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(2, elements.length);
    assertEquals("title", elements[0].getName());
    assertEquals("author", elements[1].getName());

    assertEquals("title", elementDescriptor.getElementDescriptor(XmlTestUtil.tag("title", getProject()), null).getName());
    assertNull(elementDescriptor.getElementDescriptor(UNKNOWN_TAG, null));
  }

  public void testElementDescriptor10() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "    <xsd:attributeGroup ref=\"ddd:bookAttributes\"/>" +
        "</xsd:complexType>" +
        "<xsd:attributeGroup name=\"bookAttributes\">" +
        "   <xsd:attribute name=\"isbn\" type=\"xs:string\" use=\"required\"/>" +
        "   <xsd:attribute name=\"available\" type=\"xs:string\"/>" +
        "</xsd:attributeGroup>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);

    assertEquals(2, attributes.length);
    assertEquals("isbn", attributes[0].getName());
    assertEquals("available", attributes[1].getName());

    assertEquals("isbn", elementDescriptor.getAttributeDescriptor("isbn", tag).getName());

    assertNull(elementDescriptor.getAttributeDescriptor("xxx", tag));
  }

  public void testElementDescriptor11() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "    <xsd:complexContent>" +
        "     <xsd:restriction base=\"PurchaseOrderType2\">" +
        "       <xsd:element name=\"shipTo2\" type=\"USAddress\"/>" +
        "       <xsd:element name=\"items\" type=\"Items\"/>" +
        "     </xsd:restriction>" +
        "    </xsd:complexContent>" +
        "</xsd:complexType>" +
        "<xsd:complexType name=\"PurchaseOrderType2\">" +
        "   <xsd:sequence>" +
        "     <xsd:element name=\"shipTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"billTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"items\" type=\"Items\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);

    assertEquals(4, elements.length);
    assertEquals("shipTo", elements[0].getName());
    assertEquals("billTo", elements[1].getName());
    assertEquals("shipTo2", elements[2].getName());
    assertEquals("items", elements[3].getName());
  }

  public void testElementDescriptor15() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "    <xsd:complexContent>" +
        "     <xsd:extension base=\"PurchaseOrderType2\">" +
        "       <xsd:element name=\"shipTo2\" type=\"USAddress\"/>" +
        "     </xsd:extension>" +
        "    </xsd:complexContent>" +
        "</xsd:complexType>" +
        "<xsd:complexType name=\"PurchaseOrderType2\">" +
        "   <xsd:sequence>" +
        "     <xsd:element name=\"shipTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"billTo\" type=\"USAddress\"/>" +
        "     <xsd:element name=\"items\" type=\"Items\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);

    assertEquals(4, elements.length);
    assertEquals("shipTo", elements[0].getName());
    assertEquals("billTo", elements[1].getName());
    assertEquals("items", elements[2].getName());
    assertEquals("shipTo2", elements[3].getName());
  }

  public void testElementDescriptor12() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "    <xsd:complexContent>" +
        "     <xsd:restriction base=\"PurchaseOrderType2\">" +
        "       <xsd:attribute name=\"orderDate2\" type=\"xsd:date\"/>" +
        "       <xsd:attribute name=\"name\" type=\"xsd:date\"/>" +
        "     </xsd:restriction>" +
        "    </xsd:complexContent>" +
        "</xsd:complexType>" +
        "<xsd:complexType name=\"PurchaseOrderType2\">" +
        "   <xsd:sequence>" +
        "     <xsd:attribute name=\"orderDate\" type=\"xsd:date\"/>" +
        "     <xsd:attribute name=\"name\" type=\"xsd:string\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);

    assertEquals(3, attributes.length);
    assertEquals("orderDate", attributes[0].getName());
    assertEquals("orderDate2", attributes[1].getName());
    assertEquals("name", attributes[2].getName());
  }

  public void testElementDescriptor13() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\">" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element ref=\"shipTo\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:element>" +
        "<xsd:element name=\"shipTo\" abstract=\"true\">" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    final XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(0, elements.length);
  }

  public void testElementDescriptor14() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema targetNamespace=\"http://www.deansoft.com/AddressBook\" xmlns:ab=\"http://www.deansoft.com/AddressBook\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\">" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element ref=\"shipTo\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:element>" +
        "<xsd:element name=\"shipTo\" abstract=\"true\">" +
        "   <xsd:complexType name=\"USAddress\">" +
        "       <xsd:attribute name=\"orderDate\" type=\"xsd:date\"/>" +
        "       <xsd:element name=\"items\" type=\"Items\"/>" +
        "   </xsd:complexType>" +
        "</xsd:element>" +
        "<xsd:element name=\"name\" substitutionGroup=\"ab:shipTo\"/>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", "http://www.deansoft.com/AddressBook", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    final XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(1, elements.length);
    assertEquals("name", elements[0].getName());
    XmlAttributeDescriptor[] attrs = elements[0].getAttributesDescriptors(tag);
    assertEquals(1, attrs.length);
    assertEquals("orderDate", attrs[0].getName());
    XmlElementDescriptor[] element0Descriptors = elements[0].getElementsDescriptors(tag.findFirstSubTag(elements[0].getName()));
    assertEquals(1, element0Descriptors.length);
    assertEquals("items", element0Descriptors[0].getName());
  }

  public void testAttributeDescriptor1() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:attribute name=\"orderDate\" type=\"xsd:date\"/>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    XmlAttributeDescriptor attribute = elementDescriptor.getAttributeDescriptor("orderDate", tag);

    assertTrue(!attribute.isEnumerated());
    assertTrue(!attribute.isFixed());
    assertTrue(!attribute.isRequired());
    assertNull(attribute.getDefaultValue());
  }

  public void testAttributeDescriptorProhibited() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:attribute name=\"orderDate\" type=\"xsd:date\" use=\"prohibited\" />" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);
    assertEquals(0, attributes.length);
  }

  public void testAttributeDescriptorProhibited2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "    <xsd:complexContent>" +
        "     <xsd:restriction base=\"PurchaseOrderType2\">" +
        "       <xsd:attribute name=\"orderDate\" type=\"xsd:date\" use=\"prohibited\"/>" +
        "     </xsd:restriction>" +
        "    </xsd:complexContent>" +
        "</xsd:complexType>" +
        "<xsd:complexType name=\"PurchaseOrderType2\">" +
        "   <xsd:sequence>" +
        "     <xsd:attribute name=\"orderDate\" type=\"xsd:date\"/>" +
        "     <xsd:attribute name=\"name\" type=\"xsd:string\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);

    XmlAttributeDescriptor[] attributes = elementDescriptor.getAttributesDescriptors(tag);

    assertEquals(1, attributes.length);
    assertEquals("name", attributes[0].getName());
  }

  public void testAttributeDescriptor2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:attribute name=\"orderDate\" type=\"xsd:date\" use=\"required\" default=\" 2002 \"/>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    XmlAttributeDescriptor attribute = elementDescriptor.getAttributeDescriptor("orderDate", tag);

    assertTrue(!attribute.isEnumerated());
    assertTrue(!attribute.isFixed());
    assertTrue(attribute.isRequired());
    assertEquals(" 2002 ", attribute.getDefaultValue());
  }

  public void testAttributeDescriptor3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:attribute name=\"orderDate\" type=\"xsd:date\" fixed=\"1 01 2001\"/>" +
        "</xsd:complexType>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    XmlAttributeDescriptor attribute = elementDescriptor.getAttributeDescriptor("orderDate", tag);

    assertTrue(!attribute.isEnumerated());
    assertTrue(attribute.isFixed());
    assertTrue(!attribute.isRequired());
    assertEquals("1 01 2001", attribute.getDefaultValue());
  }

  public void testAttributeDescriptor4() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:attribute ref=\"orderDate\" use=\"required\"/>" +
        "</xsd:complexType>" +
        "   <xsd:attribute name=\"orderDate\" type=\"xsd:date\" fixed=\"1 01 2001\"/>" +
        "</xsd:schema>");

    final XmlTag tag = XmlTestUtil.tag("purchaseOrder", getProject());
    XmlElementDescriptor elementDescriptor = NSDescriptor.getElementDescriptor(tag);
    XmlAttributeDescriptor attribute = elementDescriptor.getAttributeDescriptor("orderDate", tag);

    assertNotNull(attribute);
    assertTrue(!attribute.isEnumerated());
    assertTrue(attribute.isFixed());
    assertTrue(attribute.isRequired());
    assertEquals("1 01 2001", attribute.getDefaultValue());
  }

  public void testNamespace1() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xs:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xs:element name=\"comment\" type=\"xs:string\"/>" +
        "</xs:schema>");

    assertNotNull(NSDescriptor);
    assertNotNull(NSDescriptor.getElementDescriptor(XmlTestUtil.tag("purchaseOrder", getProject())));
    assertNotNull(NSDescriptor.getElementDescriptor(XmlTestUtil.tag("comment", getProject())));
    assertNull(NSDescriptor.getElementDescriptor(UNKNOWN_TAG));
  }

  public void testNamespace2() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema targetNamespace=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" >" +
        "<xsd:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<xsd:complexType name=\"PurchaseOrderType\">" +
        "   <xsd:sequence>" +
        "     <xsd:element ref=\"xsd:items\"/>" +
        "   </xsd:sequence>" +
        "</xsd:complexType>" +
        "<xsd:element name=\"items\" type=\"xsd:string\"/>" +
        "</xsd:schema>");

    XmlTag tag = XmlTestUtil.tag("purchaseOrder", "http://www.w3.org/2001/XMLSchema", getProject());
    XmlElementDescriptorImpl elementDescriptor = (XmlElementDescriptorImpl)NSDescriptor.getElementDescriptor(tag);

    XmlElementDescriptor[] elements = elementDescriptor.getElementsDescriptors(tag);
    assertEquals(1, elements.length);
    assertEquals("items", elements[0].getName());

    assertEquals("items", elementDescriptor.getElementDescriptor("items").getName());
    assertNull(elementDescriptor.getElementDescriptor("xxx"));
  }

  public void testNamespace3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xs:schema xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
        "<xs:element name=\"purchaseOrder\" type=\"PurchaseOrderType\"/>" +
        "<element name=\"comment\" type=\"xs:string\"/>" +
        "</xs:schema>");

    assertNotNull(NSDescriptor);
    assertNotNull(NSDescriptor.getElementDescriptor(XmlTestUtil.tag("purchaseOrder", "http://www.w3.org/2001/XMLSchema", getProject())));
    assertNotNull(NSDescriptor.getElementDescriptor(XmlTestUtil.tag("comment", "http://www.w3.org/2001/XMLSchema", getProject())));
    assertNull(NSDescriptor.getElementDescriptor(UNKNOWN_TAG));
  }

  //public void testAny1() throws Exception {
  //  XmlDocumentDescriptor documentDescriptor = createDescriptorImpl(
  //      "<xsd:schema targetNamespace=\"http://foo\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" >" +
  //      "  <xsd:element name=\"root\">" +
  //      "    <xsd:complexType>" +
  //      "      <xsd:sequence minOccurs=\"1\" maxOccurs=\"1\">" +
  //      "        <xsd:any namespace=\"##other\" minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"skip\"/>" +
  //      "      </xsd:sequence>" +
  //      "    </xsd:complexType>" +
  //      "  </xsd:element>" +
  //      "</xsd:schema>"
  //  );
  //
  //  XmlFile xmlFile = (XmlFile)createFile("file.xml",
  //      "<root xmlns=\"http://foo\">" +
  //      "  <a:a xmlns:a=\"http://bar\" />" +
  //      "</root>"
  //  );
  //
  //  XmlElementDescriptor rootDescriptor = documentDescriptor.getElementDescriptor(xmlFile.saveToString().getRootTag());
  //  assertNotNull(rootDescriptor);
  //
  //  XmlTag aTag = xmlFile.saveToString().getRootTag().findSubTag("a:a");
  //  assertNotNull(aTag);
  //  XmlElementDescriptor aDescriptor = documentDescriptor.getElementDescriptor(aTag);
  //  assertNotNull(aDescriptor);
  //}

  public void testAny2() {
    PsiFile dtdFile = createFile("test.xml", "<xsd:schema targetNamespace=\"http://foo\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" >" +
                                             "  <xsd:element name=\"root\">" +
                                             "    <xsd:complexType>" +
                                             "      <xsd:sequence minOccurs=\"1\" maxOccurs=\"1\">" +
                                             "        <xsd:any namespace=\"##other\" minOccurs=\"0\" maxOccurs=\"unbounded\" processContents=\"skip\"/>" +
                                             "      </xsd:sequence>" +
                                             "    </xsd:complexType>" +
                                             "  </xsd:element>" +
                                             "</xsd:schema>");

    XmlNSDescriptor NSDescriptor = new XmlNSDescriptorImpl((XmlFile)dtdFile);

    XmlFile xmlFile = (XmlFile)createFile("file.xml",
                                          "<foo:root xmlns:foo=\"http://foo\">" +
                                          "  <foo:a xmlns:a=\"http://bar\" />" +
                                          "</foo:root>"
    );

    XmlElementDescriptor rootDescriptor = NSDescriptor.getElementDescriptor(xmlFile.getDocument().getRootTag());
    assertNotNull(rootDescriptor);

    XmlTag aTag = xmlFile.getDocument().getRootTag().findFirstSubTag("foo:a");
    assertNotNull(aTag);
    //XmlElementDescriptor aDescriptor = NSDescriptor.getElementDescriptor(aTag);
    //assertNull(aDescriptor);
  }

  public void testAny3() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema targetNamespace=\"http://foo\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" >" +
        "  <xsd:element name=\"root\">" +
        "    <xsd:complexType>" +
        "        <xsd:anyAttribute namespace=\"##other\" processContents=\"skip\"/>" +
        "    </xsd:complexType>" +
        "  </xsd:element>" +
        "</xsd:schema>"
    );

    XmlFile xmlFile = (XmlFile)createFile("file.xml",
                                          "<root xmlns=\"http://foo\" y:a=\"1\">" +
                                          "</root>"
    );

    final XmlTag rootTag = xmlFile.getDocument().getRootTag();
    XmlElementDescriptor rootDescriptor = NSDescriptor.getElementDescriptor(rootTag);
    assertNotNull(rootDescriptor);

    XmlAttribute attribute = rootTag.getAttribute("y:a", XmlUtil.EMPTY_URI);
    assertNotNull(attribute);
    XmlAttributeDescriptor aDescriptor = rootDescriptor.getAttributeDescriptor("y:a", rootTag);
    assertNotNull(aDescriptor);
  }

  public void testAny4() {
    XmlNSDescriptor NSDescriptor = createDescriptor(
        "<xsd:schema targetNamespace=\"http://foo\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" >" +
        "  <xsd:element name=\"root\">" +
        "    <xsd:complexType>" +
        "        <xsd:anyAttribute namespace=\"##other\" processContents=\"skip\"/>" +
        "    </xsd:complexType>" +
        "  </xsd:element>" +
        "</xsd:schema>"
    );

    XmlFile xmlFile = (XmlFile)createFile("file.xml",
                                          "<root xmlns=\"http://foo\" a=\"1\">" +
                                          "</root>"
    );

    final XmlTag rootTag = xmlFile.getDocument().getRootTag();
    XmlElementDescriptor rootDescriptor = NSDescriptor.getElementDescriptor(rootTag);
    assertNotNull(rootDescriptor);

    XmlAttribute attribute = rootTag.getAttribute("a", XmlUtil.EMPTY_URI);
    assertNotNull(attribute);
    XmlAttributeDescriptor aDescriptor = rootDescriptor.getAttributeDescriptor("a", rootTag);
    assertNull(aDescriptor);

    attribute = rootTag.getAttribute("a", "http://foo");
    assertNull(attribute);
    attribute = rootTag.getAttribute("a", XmlUtil.ANT_URI);
    assertNull(attribute);
  }

  private static XmlNSDescriptor createDescriptor(@NonNls String dtdText) {
    PsiFile dtdFile = createFile("test.xml", dtdText);

    return new XmlNSDescriptorImpl((XmlFile)dtdFile);
  }
}
