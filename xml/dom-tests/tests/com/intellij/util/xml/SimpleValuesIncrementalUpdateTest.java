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
package com.intellij.util.xml;

import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.impl.DomTestCase;

/**
 * @author peter
 */
public class SimpleValuesIncrementalUpdateTest extends DomTestCase {

  public void testAttributeChange() {
    final MyElement element = createElement("<a/>");
    element.getXmlTag().setAttribute("attr", "foo");
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
    assertTrue(element.getAttr().isValid());

    element.getXmlTag().setAttribute("bttr", "foo");
    element.getXmlTag().setAttribute("attr", "bar");
    putExpected(new DomEvent(element, false));
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
    assertTrue(element.getAttr().isValid());

    element.getXmlTag().setAttribute("attr", null);
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
    assertTrue(element.getAttr().isValid());
  }

  public void testAttributeValueChangeAsXmlElementChange() {
    final MyElement element = createElement("<a attr=\"foo\"/>");
    final GenericAttributeValue<String> attr = element.getAttr();
    attr.getXmlAttributeValue().getFirstChild().replace(createTag("<a attr=\"bar\"/>").getAttribute("attr", null).getValueElement().getFirstChild());
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
    assertTrue(attr.isValid());
  }

  public void testTagValueChange() {
    final MyElement element = createElement("<a><child> </child></a>").getChild();
    element.getXmlTag().getValue().setText("abc");
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();

    element.getXmlTag().getValue().setText(null);
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testAttrXmlEmptyUri() {
    final MyElement element = createElement("<a xmlns=\"foo\"><ns-child attr=\"239\"/></a>" , MyElement.class);
    getDomManager().getDomFileDescription(element.getXmlElement()).registerNamespacePolicy("foo", "foo");

    final GenericAttributeValue<String> attr = element.getNsChild().getAttr();
    attr.getXmlTag().setAttribute("attr", "42");
    putExpected(new DomEvent(element.getNsChild(), false));
    assertResultsAndClear();
  }

  private MyElement createElement(final String xml) throws IncorrectOperationException {
    return createElement(xml, MyElement.class);
  }

  public interface MyElement extends DomElement{
    GenericAttributeValue<String> getAttr();

    Integer getValue();

    MyElement getChild();

    MyNsElement getNsChild();
  }

  @Namespace("foo")
  public interface MyNsElement extends DomElement{
    GenericAttributeValue<String> getAttr();

  }

}
