package com.intellij.util.xml;

import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.events.DomEvent;

/**
 * @author peter
 */
public class SimpleValuesIncrementalUpdateTest extends DomTestCase{

  public void testAttributeChange() throws Throwable {
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

  public void testAttributeValueChangeAsXmlElementChange() throws Throwable {
    final MyElement element = createElement("<a attr=\"foo\"/>");
    final GenericAttributeValue<String> attr = element.getAttr();
    attr.getXmlAttributeValue().getFirstChild().replace(createTag("<a attr=\"bar\"/>").getAttribute("attr", null).getValueElement().getFirstChild());
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
    assertTrue(attr.isValid());
  }

  public void testTagValueChange() throws Throwable {
    final MyElement element = createElement("<a><child> </child></a>").getChild();
    element.getXmlTag().getValue().setText("abc");
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();

    element.getXmlTag().getValue().setText(null);
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testAttrXmlEmptyUri() throws Throwable {
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
