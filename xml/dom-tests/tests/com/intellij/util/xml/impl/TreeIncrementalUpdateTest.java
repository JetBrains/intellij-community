/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class TreeIncrementalUpdateTest extends DomTestCase {

  public void testRenameCollectionTag() {
    final MyElement rootElement = createPhysicalElement(
        "<?xml version='1.0' encoding='UTF-8'?>\n" + "<a>\n" + " <boy>\n" + " </boy>\n" + " <girl/>\n" + "</a>");
    myCallRegistry.clear();
    assertEquals(1, rootElement.getBoys().size());
    assertEquals(1, rootElement.getGirls().size());
    final MyElement oldBoy = rootElement.getBoys().get(0);
    final XmlTag tag = oldBoy.getXmlTag();
    assertNotNull(tag);
    final int offset = tag.getTextOffset();
    final int endoffset = offset+tag.getTextLength();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        rootElement.getGirls().get(0).undefine();
        final Document document = getDocument(DomUtil.getFile(rootElement));
        PsiDocumentManager.getInstance(getProject()).doPostponedOperationsAndUnblockDocument(document);
        document.replaceString(offset+1, offset+1+"boy".length(), "girl");
        commitDocument(document);
      }
    }.execute();
    assertFalse(oldBoy.isValid());
    assertEquals(0, rootElement.getBoys().size());
    assertEquals(1, rootElement.getGirls().size());
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final Document document = getDocument(DomUtil.getFile(rootElement));
        document.replaceString(endoffset - "boy".length(), endoffset, "girl");
        commitDocument(document);
      }
    }.execute();
    assertEquals(0, rootElement.getBoys().size());
    assertEquals(1, rootElement.getGirls().size());
  }

  private MyElement createPhysicalElement(final String text) throws IncorrectOperationException {
    final XmlFile file = (XmlFile)createFile("file.xml", text);
    final DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class, "a");
    final MyElement rootElement = fileElement.getRootElement();
    return rootElement;
  }

  public void testRenameFixedTag() {
    final XmlFile file = (XmlFile)createFile("file.xml", "<?xml version='1.0' encoding='UTF-8'?>\n" +
                                                         "<a>\n" +
                                                         " <aboy>\n" +
                                                         " </aboy>\n" +
                                                         " <agirl/>\n" +
                                                         "</a>");
    final DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class, "a");
    myCallRegistry.clear();
    final MyElement rootElement = fileElement.getRootElement();
    assertNotNull(rootElement.getAboy().getXmlElement());
    assertNotNull(rootElement.getAgirl().getXmlElement());
    final MyElement oldBoy = rootElement.getAboy();
    final XmlTag tag = oldBoy.getXmlTag();
    assertNotNull(tag);
    final int offset = tag.getTextOffset();
    final int endoffset = offset+tag.getTextLength();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        rootElement.getAgirl().undefine();
        final Document document = getDocument(file);
        PsiDocumentManager.getInstance(getProject()).doPostponedOperationsAndUnblockDocument(document);
        document.replaceString(offset+1, offset+1+"aboy".length(), "agirl");
        commitDocument(document);
      }
    }.execute();
    assertFalse(oldBoy.isValid());
    assertNull(rootElement.getAboy().getXmlElement());
    assertNotNull(rootElement.getAgirl().getXmlElement());
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final Document document = getDocument(file);
        document.replaceString(endoffset - "aboy".length(), endoffset, "agirl");
        commitDocument(document);
      }
    }.execute();
    assertNull(rootElement.getAboy().getXmlElement());
    assertNotNull(rootElement.getAgirl().getXmlElement());
  }

  public void testDocumentChange() {
    final XmlFile file = (XmlFile)createFile("file.xml", "<?xml version='1.0' encoding='UTF-8'?>\n" +
                                                         "<a>\n" +
                                                         " <child>\n" +
                                                         "  <child/>\n" +
                                                         " </child>\n" +
                                                         "</a>");
    final DomFileElementImpl<MyElement> fileElement =
      getDomManager().getFileElement(file, MyElement.class, "a");
    myCallRegistry.clear();
    final MyElement rootElement = fileElement.getRootElement();
    final MyElement oldLeaf = rootElement.getChild().getChild();
    final XmlTag oldLeafTag = oldLeaf.getXmlTag();

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final Document document = getDocument(file);
        document.replaceString(0, document.getText().length(), "<a/>");
        commitDocument(document);
      }
    }.execute();
    assertFalse(oldLeafTag.isValid());

    putExpected(new DomEvent(fileElement, false));
    assertResultsAndClear();

    assertEquals(fileElement.getRootElement(), rootElement);
    assertTrue(rootElement.isValid());

    assertFalse(oldLeaf.isValid());

    assertTrue(rootElement.getChild().isValid());
    assertNull(rootElement.getChild().getXmlTag());
    assertNull(rootElement.getChild().getChild().getXmlTag());
  }

  public void testDocumentChange2() {
    final XmlFile file = (XmlFile)createFile("file.xml", "<?xml version='1.0' encoding='UTF-8'?>\n" +
                                                         "<!DOCTYPE ejb-jar PUBLIC \"-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN\" \"http://java.sun.com/dtd/ejb-jar_2_0.dtd\">\n" +
                                                         "<a>\n" +
                                                         " <child>\n" +
                                                         "  <child/>\n" +
                                                         " </child>\n" +
                                                         "</a>");
    final DomFileElementImpl<MyElement> fileElement =
      getDomManager().getFileElement(file, MyElement.class, "a");
    myCallRegistry.clear();
    final MyElement rootElement = fileElement.getRootElement();
    final MyElement oldLeaf = rootElement.getChild().getChild();
    final XmlTag oldLeafTag = oldLeaf.getXmlTag();

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        file.getDocument().getProlog().delete();
        final XmlTag tag = file.getDocument().getRootTag();
        tag.setAttribute("xmlns", "something");
        tag.setAttribute("xmlns:xsi", "something");
      }
    }.execute();

    assertTrue(oldLeafTag.isValid());

    putExpected(new DomEvent(fileElement, false));
    putExpected(new DomEvent(rootElement, false));
    putExpected(new DomEvent(rootElement, false));
    assertResultsAndClear();

    assertEquals(fileElement.getRootElement(), rootElement);
    assertTrue(rootElement.isValid());

    assertTrue(rootElement.getChild().isValid());
    assertTrue(rootElement.getChild().getXmlTag().isValid());
    assertTrue(rootElement.getChild().getChild().getXmlTag().isValid());
  }

  public void testMoveUp() {
    final XmlFile file = (XmlFile)createFile("file.xml", "<?xml version='1.0' encoding='UTF-8'?>\n" +
                                                         "<a>\n" +
                                                         " <child>\n" +
                                                         "  <aboy />\n" +
                                                         "  <agirl/>\n" +
                                                         " </child>\n" +
                                                         "</a>");
    final DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class, "a");
    myCallRegistry.clear();
    final MyElement rootElement = fileElement.getRootElement();
    rootElement.getChild().getAboy();
    rootElement.getChild().getAgirl();

    final Document document = getDocument(file);
    final int len = "<agirl/>".length();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final int agirl = document.getText().indexOf("<agirl/>");
        final int boy = document.getText().indexOf("<aboy />");
        document.replaceString(agirl, agirl + len, "<aboy />");
        document.replaceString(boy, boy + len, "<agirl/>");
        commitDocument(document);
      }
    }.execute();

    assertTrue(rootElement.isValid());
    final XmlTag tag1 = rootElement.getXmlTag().getSubTags()[0];
    assertEquals(getDomManager().getDomElement(tag1.findFirstSubTag("agirl")), rootElement.getChild().getAgirl());
    assertEquals(getDomManager().getDomElement(tag1.findFirstSubTag("aboy")), rootElement.getChild().getAboy());
  }

  public void testRemoveAttributeParent() {
    final XmlFile file = (XmlFile)createFile("file.xml", "<?xml version='1.0' encoding='UTF-8'?>\n" +
                                                         "<!DOCTYPE ejb-jar PUBLIC \"-//Sun Microsystems, Inc.//DTD Enterprise JavaBeans 2.0//EN\" \"http://java.sun.com/dtd/ejb-jar_2_0.dtd\">\n" +
                                                         "<a>\n" +
                                                         " <child-element xxx=\"239\"/>\n" +
                                                         "</a>");
    final DomFileElementImpl<MyElement> fileElement =
      getDomManager().getFileElement(file, MyElement.class, "a");
    myCallRegistry.clear();
    final MyElement rootElement = fileElement.getRootElement();
    final MyElement oldLeaf = rootElement.getChildElements().get(0);
    final GenericAttributeValue<String> xxx = oldLeaf.getXxx();
    final XmlTag oldLeafTag = oldLeaf.getXmlTag();

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        oldLeafTag.delete();
      }
    }.execute();

    assertFalse(oldLeaf.isValid());
    assertFalse(xxx.isValid());
  }

  public void testTypeBeforeRootTag() {
    getDomManager().registerFileDescription(new DomFileDescription<>(MyElement.class, "a"), getTestRootDisposable());

    final XmlFile file = (XmlFile)createFile("file.xml", "<?xml version='1.0' encoding='UTF-8'?>\n" +
                                                         "<a/>");

    assertTrue(getDomManager().isDomFile(file));
    final DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class);
    assertTrue(fileElement.isValid());
    myCallRegistry.clear();

    putExpected(new DomEvent(fileElement, false));

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final Document document = getDocument(file);
        final int i = document.getText().indexOf("<a");
        document.insertString(i, "a");
        commitDocument(document);
      }
    }.execute();

    assertFalse(getDomManager().isDomFile(file));
    assertFalse(fileElement.isValid());
    assertResultsAndClear();
  }

  private void assertNoCache(XmlTag tag) {
    assertNull(tag.getText(), getCachedHandler(tag));
    if (tag.isValid()) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        assertNoCache(xmlTag);
      }
    }
  }

  private MyElement createElement(final String xml) throws IncorrectOperationException {
    return createElement(xml, MyElement.class);
  }

  public void testAddCollectionElement() {
    final MyElement element = createElement("<a><child/><child/><child-element/></a>");
    final MyElement child = element.getChild();
    final MyElement child2 = element.getChild2();
    final MyElement firstChild = element.getChildElements().get(0);
    element.getXmlTag().add(createTag("<child-element/>"));
    final XmlTag[] subTags = element.getXmlTag().getSubTags();
    assertEquals(2, element.getChildElements().size());
    assertEquals(firstChild, element.getChildElements().get(0));
    MyElement nextChild = element.getChildElements().get(1);

    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testAddFixedElement() {
    final MyElement element = createPhysicalElement("<a>" +
                                            "<child/>" +
                                            "<child><child/></child>" +
                                            "<child/></a>");
    final MyElement child = element.getChild();
    final MyElement child2 = element.getChild2();
    final XmlTag leafTag = child2.getChild().getXmlTag();

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        element.getXmlTag().addAfter(createTag("<child/>"), child.getXmlTag());
      }
    }.execute();

    assertNoCache(leafTag);

    final XmlTag[] subTags = element.getXmlTag().getSubTags();

    assertFalse(child2.isValid());
    assertEquals(child, element.getChild());
    assertFalse(child2.equals(element.getChild2()));

    assertCached(child, subTags[0]);
    assertNoCache(subTags[2]);
    assertNoCache(subTags[3]);

    putExpected(new DomEvent(element, false));
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testAddFixedElementCanDefineIt() {
    final MyElement element = createElement("<a></a>");
    final MyElement child = element.getChild();

    element.getXmlTag().add(createTag("<child/>"));

    final XmlTag[] subTags = element.getXmlTag().getSubTags();

    assertTrue(child.equals(element.getChild()));
    assertTrue(element.getChild().equals(child));

    assertCached(element.getChild(), subTags[0]);

    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testActuallyRemoveCollectionElement() {
    final MyElement element = createElement("<a><child-element><child/></child-element><child-element/></a>");
    final MyElement child = element.getChild();
    final MyElement child2 = element.getChild2();
    final MyElement firstChild = element.getChildElements().get(0);
    final MyElement lastChild = element.getChildElements().get(1);

    final XmlTag tag = element.getXmlTag();
    final XmlTag childTag = tag.getSubTags()[0];
    WriteCommandAction.runWriteCommandAction(null, () -> childTag.delete());

    putExpected(new DomEvent(element, false));
    assertResultsAndClear();

    assertEquals(child, element.getChild());
    assertEquals(child2, element.getChild2());
    assertEquals(Arrays.asList(lastChild), element.getChildElements());
    assertCached(lastChild, tag.getSubTags()[0]);
  }

  public void testCustomChildrenEvents() {
    final Sepulka element = createElement("<a><foo/><bar/></a>", Sepulka.class);
    final List<MyElement> list = element.getCustomChildren();
    final XmlTag tag = element.getXmlTag();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.getSubTags()[0].delete();
      tag.getSubTags()[0].delete();
    });

    tag.add(createTag("<goo/>"));
    putExpected(new DomEvent(element, false));
    putExpected(new DomEvent(element, false));
    putExpected(new DomEvent(element, false));
    assertResultsAndClear();

    assertEquals(1, element.getCustomChildren().size());
  }

  public void testRemoveFixedElement() {
    final MyElement element = createElement("<a>" +
                                            "<child/>" +
                                            "<child><child/></child>" +
                                            "<child><child/></child>" +
                                            "</a>");
    final MyElement child = element.getChild();
    final MyElement child2 = element.getChild2();
    final MyElement oldLeaf = child2.getChild();
    final XmlTag tag = element.getXmlTag();
    XmlTag leafTag = tag.getSubTags()[2].getSubTags()[0];
    assertNoCache(leafTag);

    ApplicationManager.getApplication().runWriteAction(() -> {
      tag.getSubTags()[1].delete();

      assertFalse(oldLeaf.isValid());

      putExpected(new DomEvent(element, false));
      assertResultsAndClear();

      assertEquals(child, element.getChild());
      assertFalse(child2.isValid());

      tag.getSubTags()[1].delete();
    });

    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testRootTagAppearsLater() {
    final XmlFile file = createXmlFile("");
    final DomFileElementImpl<MyElement> fileElement = getDomManager().getFileElement(file, MyElement.class, "root");
    myCallRegistry.clear();

    assertNull(fileElement.getRootElement().getXmlTag());

    file.getDocument().replace(createXmlFile("<root/>").getDocument());
    final XmlTag rootTag = fileElement.getRootTag();
    assertEquals(rootTag, file.getDocument().getRootTag());
    putExpected(new DomEvent(fileElement.getRootElement(), false));
    assertResultsAndClear();
  }

  public void testAnotherChildren() {
    final MyElement element = createElement("<a><child/></a>");
    element.getXmlTag().add(createTag("<another-child/>"));
    assertEquals(1, element.getAnotherChildren().size());

    putExpected(new DomEvent(element, false));
    assertResultsAndClear();
  }

  public void testInvalidateParent() {
    final MyElement root = getDomManager().createMockElement(MyElement.class, null, true);
    new WriteCommandAction<MyElement>(getProject()) {
      @Override
      protected void run(@NotNull Result<MyElement> result) {
        root.getChild().ensureTagExists();
        root.getChild2().ensureTagExists();
        final MyElement element = root.addChildElement().getChild();
        result.setResult(element);
        element.ensureTagExists().getValue().setText("abc");
        root.addChildElement();
        root.addChildElement();
      }
    }.execute().getResultObject();
    assertTrue(root.isValid());
    final MyElement element = root.getChildElements().get(0).getChild();
    assertTrue(element.isValid());
    final MyElement child = element.getChild();
    final MyElement genericValue = child.getChild();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        final Document document = getDocument(DomUtil.getFile(element));
        final TextRange range = element.getXmlTag().getTextRange();
        document.replaceString(range.getStartOffset(), range.getEndOffset(), "");
        commitDocument(document);
      }
    }.execute();
    assertFalse(genericValue.isValid());
    assertFalse(child.isValid());
    assertFalse(element.isValid());
  }

  public void testCollectionChildValidAfterFormattingReparse() {
    final MyElement root = getDomManager().createMockElement(MyElement.class, null, true);
    final MyElement element = new WriteCommandAction<MyElement>(getProject()) {
      @Override
      protected void run(@NotNull Result<MyElement> result) {
        result.setResult(root.addChildElement());
      }
    }.execute().getResultObject();
    assertTrue(root.isValid());
    assertNotNull(element.getXmlElement());
  }

  public void testChangeImplementationClass() {
    getTypeChooserManager().registerTypeChooser(MyElement.class, createClassChooser());
    try {
      final MyElement element = getDomManager().createMockElement(MyElement.class, getModule(), true);
      final DomFileElement<MyElement> root = DomUtil.getFileElement(element);
      
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) {
          element.addChildElement().addChildElement();
        }
      }.execute();

      final MyElement child = element.getChildElements().get(0);
      MyElement grandChild = child.getChildElements().get(0);
      assertTrue(child instanceof BarInterface);
      assertTrue(grandChild instanceof BarInterface);

      grandChild = element.getChildElements().get(0).getChildElements().get(0);
      final XmlTag tag = grandChild.getXmlTag();
      assertTrue(grandChild.isValid());
      assertEquals(grandChild, root.getRootElement().getChildElements().get(0).getChildElements().get(0));
      assertNotNull(element.getXmlTag());
      assertNotNull(child.getXmlTag());
      assertNotNull(tag);
      assertTrue(tag.isValid());

      myCallRegistry.clear();

      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) {
          tag.add(XmlElementFactory.getInstance(getProject()).createTagFromText("<foo/>"));
        }
      }.execute();

      assertTrue(root.isValid());
      assertTrue(element.isValid());
      assertTrue(grandChild.isValid());
      final MyElement newChild = root.getRootElement().getChildElements().get(0);
      assertTrue(newChild instanceof BarInterface);

      final MyElement newGrandChild = newChild.getChildElements().get(0);
      assertTrue(newGrandChild.isValid());
      assertTrue(newGrandChild instanceof FooInterface);

      putExpected(new DomEvent(child, false));
      putExpected(new DomEvent(grandChild, false));
      assertResultsAndClear();
    } finally {
      getTypeChooserManager().unregisterTypeChooser(MyElement.class);
    }
  }

  public void testChangeImplementationClass_InCollection() {
    getTypeChooserManager().registerTypeChooser(MyElement.class, createClassChooser());
    try {
      final MyElement element = getDomManager().createMockElement(MyElement.class, getModule(), true);
      final DomFileElement<MyElement> root = DomUtil.getFileElement(element);
      new WriteCommandAction<MyElement>(getProject()) {
        @Override
        protected void run(@NotNull Result<MyElement> result) {
          element.addChildElement().addChildElement();
        }
      }.execute().getResultObject();
      final MyElement child = element.getChildElements().get(0);
      final MyElement grandChild = child.getChildElements().get(0);
      assertTrue(child instanceof BarInterface);
      assertTrue(grandChild instanceof BarInterface);

      assertTrue(element.isValid());
      assertTrue(child.isValid());
      assertTrue(grandChild.isValid());

      assertNotNull(element.getXmlTag());
      assertNotNull(child.getXmlTag());

      final XmlTag tag = grandChild.getXmlTag();
      assertNotNull(tag);

      myCallRegistry.clear();

      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) {
          tag.add(XmlElementFactory.getInstance(getProject()).createTagFromText("<foo/>"));
        }
      }.execute();

      assertTrue(root.isValid());
      assertTrue(element.isValid());

      assertTrue(child.isValid());
      final MyElement newChild = element.getChildElements().get(0);
      assertTrue(newChild.isValid());
      assertTrue(newChild.getClass().toString(), newChild instanceof BarInterface);

      assertTrue(grandChild.isValid());
      final MyElement newGrandChild = newChild.getChildElements().get(0);
      assertTrue(newGrandChild.isValid());
      assertTrue(newGrandChild instanceof FooInterface);

      putExpected(new DomEvent(child, false));
      putExpected(new DomEvent(grandChild, false));
      assertResultsAndClear();
    } finally {
      getTypeChooserManager().unregisterTypeChooser(MyElement.class);
    }
  }
  /*

  public void testCommentTag() throws Throwable {
    final String prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE application PUBLIC \"-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN\" \"http://java.sun.com/dtd/application_1_3.dtd\">\n" +
                        "<application>\n" + "  <display-name>MyAppa</display-name>\n" + "    <description>ss</description>\n" +
                        "    <module id=\"MyWebApp2\">\n" + "        <web>    a\n" + "            <web-uri>MyWebApp2.war</web-uri>\n" +
                        "            ";
    final String infix = "<context-root>MyWebApp2</context-root>";
    String suffix = "\n" + "        </web>\n" + "    </module>\n" +
                        "    <module id=\"MyWebApp22\">\n" + "        <web>\n" + "            <web-uri>MyWebApp2.war</web-uri>\n" +
                        "            <context-root>MyWebApp2</context-root>\n" + "        </web>\n" + "    </module>\n" +
                        "    <module id=\"MyEjb32\">\n" + "        <ejb>MyEjb32.jar</ejb>\n" + "    </module>\n" +
                        "    <module id=\"MyEjb4\">\n" + "        <ejb>MyEjb4.jar</ejb>\n" + "    </module>\n" + "    <security-role>\n" +
                        "        <description>asdf</description>\n" + "        <role-name>nameasdf</role-name>\n" +
                        "    </security-role>\n" + "\n" + "</application>";

    final XmlFile file = (XmlFile)createFile("web.xml", prefix + infix + suffix);
    final JavaeeApplication javaeeApplication = getDomManager().getFileElement(file, JavaeeApplication.class).getRootElement();
    assertEquals("MyWebApp2", javaeeApplication.getModules().get(0).getWeb().getContextRoot().getValue());

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final Document document = getDocument(file);
        document.insertString(prefix.length() + infix.length(), "-->");
        document.insertString(prefix.length(), "<!--");
        commitDocument(document);
      }
    }.execute();
    assertTrue(javaeeApplication.isValid());
    assertTrue(javaeeApplication.getModules().get(0).getWeb().getContextRoot().isValid());
    assertTrue(javaeeApplication.getModules().get(0).getWeb().isValid());
    assertNull(javaeeApplication.getModules().get(0).getWeb().getContextRoot().getXmlElement());
  }

  */

  private static TypeChooser createClassChooser() {
    return new TypeChooser() {
      @Override
      public Type chooseType(final XmlTag tag) {
        return tag != null && tag.findFirstSubTag("foo") != null
               ? FooInterface.class
               : BarInterface.class;
      }

      @Override
      public void distinguishTag(final XmlTag tag, final Type aClass)
        throws IncorrectOperationException {
        if (FooInterface.class.equals(aClass) && tag.findFirstSubTag("foo") == null) {
          tag.add(XmlElementFactory.getInstance(getProject()).createTagFromText("<foo/>"));
        }
      }

      @Override
      public Type[] getChooserTypes() {
        return new Class[]{FooInterface.class,
                           BarInterface.class};
      }
    };
  }


  public interface MyElement extends DomElement{
    MyElement getChild();
    @SubTag(value="child",index=1) MyElement getChild2();
    List<MyElement> getChildElements();
    List<MyElement> getBoys();
    List<MyElement> getGirls();
    List<MyElement> getAnotherChildren();
    MyElement addChildElement();
    GenericAttributeValue<String> getXxx();

    MyElement getAboy();
    MyElement getAgirl();
  }

  public interface FooInterface extends MyElement {
  }

  public interface BarInterface extends MyElement {
  }

  public interface Sepulka extends DomElement{
    @CustomChildren
    List<MyElement> getCustomChildren();
  }

}
