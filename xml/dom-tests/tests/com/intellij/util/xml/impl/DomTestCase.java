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
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class DomTestCase extends LightIdeaTestCase {
  protected CallRegistry<DomEvent> myCallRegistry;
  private final DomEventListener myListener = new DomEventListener() {
    @Override
    public void eventOccured(@NotNull DomEvent event) {
      myCallRegistry.putActual(event);
    }
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCallRegistry = new CallRegistry<>();
    getDomManager().addDomEventListener(myListener, getTestRootDisposable());
  }

  protected void assertCached(final DomElement element, final XmlElement xmlElement) {
    assertNotNull(xmlElement);
    assertSame(element.getXmlTag(), xmlElement);
    DomInvocationHandler currentDom = getDomManager().getDomHandler(xmlElement);
    assertNotNull(currentDom);
    assertEquals(element, currentDom.getProxy());
    assertTrue(element.isValid());
  }

  protected void assertCached(DomFileElementImpl<?> element, final XmlFile file) {
    assertNotNull(file);
    assertEquals(element, getDomManager().getFileElement(file));
  }

  protected XmlTag createTag(final String text) throws IncorrectOperationException {
    return XmlElementFactory.getInstance(getProject()).createTagFromText(text);
  }

  protected DomManagerImpl getDomManager() {
    return (DomManagerImpl)DomManager.getDomManager(getProject());
  }

  protected XmlFile createXmlFile(@NonNls final String text) throws IncorrectOperationException {
    return (XmlFile)createLightFile("a.xml", text);
  }


  protected <T extends DomElement> T createElement(final String xml, final Class<T> aClass) throws IncorrectOperationException {
    final DomManagerImpl domManager = getDomManager();
    T element = createElement(domManager, xml, aClass);
    myCallRegistry.clear();
    return element;
  }

  public static <T extends DomElement> T createElement(final DomManager domManager, final String xml, final Class<T> aClass)
    throws IncorrectOperationException {
    final String name = "a.xml";
    //noinspection deprecation
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(domManager.getProject()).createFileFromText(name, xml);
    final XmlTag tag = file.getDocument().getRootTag();
    final String rootTagName = tag != null ? tag.getName() : "root";
    //noinspection deprecation
    final T element = domManager.getFileElement(file, aClass, rootTagName).getRootElement();
    assertNotNull(element);
    assertSame(tag, element.getXmlTag());
    return element;
  }

  protected void putExpected(final DomEvent event) {
    myCallRegistry.putExpected(event);
  }

  protected void assertResultsAndClear() {
    myCallRegistry.assertResultsAndClear();
  }

  protected TypeChooserManager getTypeChooserManager() {
    return getDomManager().getTypeChooserManager();
  }

  protected void incModCount() {
    getPsiManager().dropPsiCaches();
  }

  public enum MyEnum implements NamedEnum {
    FOO("foo"),
    BAR("bar");

    private final String myName;

    MyEnum(final String name) {
      myName = name;
    }

    @Override
    public String getValue() {
      return myName;
    }
  }
}
