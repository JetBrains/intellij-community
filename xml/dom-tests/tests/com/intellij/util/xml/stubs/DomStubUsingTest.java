/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.xml.stubs;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.stubs.model.Bar;
import com.intellij.util.xml.stubs.model.Foo;
import com.intellij.util.xml.stubs.model.NotStubbed;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DomStubUsingTest extends DomStubTest {

  public void testFoo() {
    DomFileElement<Foo> fileElement = prepare("foo.xml", Foo.class);
    PsiFile file = fileElement.getFile();
    assertFalse(file.getNode().isParsed());

    Foo foo = fileElement.getRootElement();
    assertEquals("foo", foo.getId().getValue());
    assertFalse(file.getNode().isParsed());

    List<Bar> bars = foo.getBars();
    assertFalse(file.getNode().isParsed());

    final List<GenericDomValue<String>> listElements = foo.getLists();
    final GenericDomValue<String> listElement0 = listElements.get(0);
    assertEquals("list0", listElement0.getValue());
    final GenericDomValue<String> listElement1 = listElements.get(1);
    assertEquals("list1", listElement1.getValue());
    assertFalse(file.getNode().isParsed());

    assertEquals(2, bars.size());
    Bar bar = bars.get(0);
    String value = bar.getString().getStringValue();
    assertEquals("xxx", value);

    Object o = bar.getString().getValue();
    assertEquals("xxx", o);

    Integer integer = bar.getInt().getValue();
    assertEquals(666, integer.intValue());

    assertFalse(file.getNode().isParsed());

    Bar emptyBar = bars.get(1);
    GenericAttributeValue<String> string = emptyBar.getString();
    assertNull(string.getXmlElement());

    assertFalse(file.getNode().isParsed());
  }

  public void testAccessingPsi() {
    DomFileElement<Foo> element = prepare("foo.xml", Foo.class);
    assertNotNull(element.getXmlElement());

    XmlTag tag = element.getRootTag();
    assertNotNull(tag);

    Foo foo = element.getRootElement();
    assertNotNull(foo.getXmlTag());

    Bar bar = foo.getBars().get(0);
    assertNotNull(bar.getXmlElement());

    XmlAttribute attribute = bar.getString().getXmlAttribute();
    assertNotNull(attribute);
  }

  public void testConverters() {
    DomFileElement<Foo> element = prepare("converters.xml", Foo.class);
    Bar bar = element.getRootElement().getBars().get(0);
    PsiClass value = bar.getClazz().getValue();
    assertNotNull(value);
    assertEquals("java.lang.String", value.getQualifiedName());
    assertFalse(element.getFile().getNode().isParsed());
  }

  public void testParent() {
    DomFileElement<Foo> element = prepare("parent.xml", Foo.class);

    Bar bar = element.getRootElement().getBars().get(0);
    GenericAttributeValue<Integer> notStubbed = bar.getNotStubbed();
    DomElement parent = notStubbed.getParent();
    assertEquals(bar, parent);

    NotStubbed child = bar.getNotStubbeds().get(0);
    parent = child.getParent();
    assertEquals(bar, parent);
  }

  public void testChildrenOfType() {
    DomFileElement<Foo> element = prepare("foo.xml", Foo.class);
    Foo foo = element.getRootElement();
    List<Bar> bars = DomUtil.getChildrenOf(foo, Bar.class);
    assertEquals(2, bars.size());
  }

  public void testFileLoading() {
    XmlFile file = prepareFile("foo.xml");
    ((PsiManagerEx)getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, myFixture.getTestRootDisposable());
    DomFileElement<Foo> element = DomManager.getDomManager(getProject()).getFileElement(file, Foo.class);
    assertNotNull(element);
    GenericDomValue<String> id = element.getRootElement().getId();
    assertEquals("foo", id.getValue());
  }

  public void testStubbedElementUndefineNotExisting() {
    final DomFileElement<Foo> fileElement = prepare("foo.xml", Foo.class);
    final Bar bar = fileElement.getRootElement().getBars().get(0);

    assertUndefine(bar);
  }

  public void testRootElementUndefineNotExisting() {
    final DomFileElement<Foo> fileElement = prepare("foo.xml", Foo.class);

    final DomElement rootElement = fileElement.getRootElement();
    assertUndefine(rootElement);
  }

  private static void assertUndefine(final DomElement domElement) {
    assertNotNull(domElement);
    assertTrue(domElement.exists());

    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() {
        domElement.undefine();
      }
    }.execute().throwException();

    assertFalse(domElement.exists());
  }
}
