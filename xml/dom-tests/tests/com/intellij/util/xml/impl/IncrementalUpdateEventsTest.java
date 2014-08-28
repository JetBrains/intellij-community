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
package com.intellij.util.xml.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
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

  public void testRemove0() {
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

  public void testRemove1() {
    deleteTag(1);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testRemove2() {
    deleteTag(2);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();

    deleteTag(2);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testRemove3() {
    deleteTag(3);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd0() {
    addChildTag(0);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd1() {
    addChildTag(1);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd2() {
    addChildElementTag(2);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd3() {
    addChildElementTag(3);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  public void testAdd4() {
    final XmlTag tag = myElement.getXmlTag();
    tag.addAfter(createTag("<child-element/>"), tag.getSubTags()[3]);
    putExpected(new DomEvent(myElement, false));
    assertResultsAndClear();
  }

  private MyElement getChild(final int index) {
    return myElement.getChildElements().get(index);
  }

  private void addChildTag(int index) {
    final XmlTag tag = myElement.getXmlTag();
    tag.addBefore(createTag("<child/>"), tag.getSubTags()[index]);
  }

  private void addChildElementTag(int index) {
    final XmlTag tag = myElement.getXmlTag();
    tag.addBefore(createTag("<child-element/>"), tag.getSubTags()[index]);
  }


  private void deleteTag(final int index) {
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        myElement.getXmlTag().getSubTags()[index].delete();
      }
    });
  }

  private MyElement createElement(final String xml) {
    return createElement(xml, MyElement.class);
  }

  public interface MyElement extends DomElement {
    MyElement getChild();

    @SubTag(value = "child", index = 1)
    MyElement getChild2();

    List<MyElement> getChildElements();
  }
}
