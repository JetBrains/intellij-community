package com.intellij.util.xml.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTestCase;
import com.intellij.util.xml.SubTag;
import com.intellij.util.xml.events.DomEvent;

import java.util.List;

/**
 * @author peter
 */
public class IncrementalUpdateEventsTest extends DomTestCase {
  private MyElement myElement;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myElement = createElement("<a><child/><child/><child-element/><child-element/></a>");
  }

  public void testRemove0() throws IncorrectOperationException {
    deleteTag(0);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();

    deleteTag(0);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();

    deleteTag(0);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();

    deleteTag(0);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testRemove1() throws IncorrectOperationException {
    deleteTag(1);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testRemove2() throws IncorrectOperationException {
    deleteTag(2);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();

    deleteTag(2);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testRemove3() throws IncorrectOperationException {
    deleteTag(3);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd0() throws Throwable {
    addChildTag(0);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd1() throws Throwable {
    addChildTag(1);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd2() throws Throwable {
    addChildElementTag(2);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd3() throws Throwable {
    addChildElementTag(3);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd4() throws Throwable {
    final XmlTag tag = myElement.getXmlTag();
    tag.addAfter(createTag("<child-element/>"), tag.getSubTags()[3]);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  private MyElement getChild(final int index) {
    return myElement.getChildElements().get(index);
  }

  private void addChildTag(int index) throws IncorrectOperationException {
    final XmlTag tag = myElement.getXmlTag();
    tag.addBefore(createTag("<child/>"), tag.getSubTags()[index]);
  }

  private void addChildElementTag(int index) throws IncorrectOperationException {
    final XmlTag tag = myElement.getXmlTag();
    tag.addBefore(createTag("<child-element/>"), tag.getSubTags()[index]);
  }


  private void deleteTag(final int index) throws IncorrectOperationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myElement.getXmlTag().getSubTags()[index].delete();
      }
    });
  }

  private MyElement createElement(final String xml) throws IncorrectOperationException {
    return createElement(xml, MyElement.class);
  }

  public interface MyElement extends DomElement {
    MyElement getChild();

    @SubTag(value = "child", index = 1)
    MyElement getChild2();

    List<MyElement> getChildElements();
  }


}
