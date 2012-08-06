/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.MockDomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomFileDescriptionTest extends DomHardCoreTestCase {
  private XmlFile myFooElementFile;
  private XmlFile myBarElementFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFooElementFile = new WriteCommandAction<XmlFile>(getProject()) {
      @Override
      protected void run(Result<XmlFile> result) throws Throwable {
        result.setResult((XmlFile)createFile("a.xml", "<a/>"));
      }
    }.execute().getResultObject();

    getDomManager().registerFileDescription(new MockDomFileDescription<FooElement>(FooElement.class, "a", myFooElementFile), getTestRootDisposable());

    myBarElementFile = new WriteCommandAction<XmlFile>(getProject()) {
      @Override
      protected void run(Result<XmlFile> result) throws Throwable {
        result.setResult((XmlFile)createFile("b.xml", "<b/>"));
      }
    }.execute().getResultObject();

    getDomManager().registerFileDescription(new DomFileDescription<BarElement>(BarElement.class, "b") {

      @Override
      public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
        return myFooElementFile.getText().contains("239");
      }

      @Override
      public boolean isAutomaticHighlightingEnabled() {
        return false;
      }
    }, getTestRootDisposable());

    assertResultsAndClear();
  }

  public void testNoInitialDomnessInB() throws Throwable {
    assertFalse(getDomManager().isDomFile(myBarElementFile));
    assertNull(getDomManager().getFileElement(myBarElementFile));
  }

  public void testIsDomValue() throws Throwable {
    final XmlFile file = (XmlFile)createFile("a.xml", "<b>42</b>");
    getDomManager().registerFileDescription(new DomFileDescription(MyElement.class, "b") {

      @Override
      public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
        return /*super.isMyFile(file, module) && */file.getText().contains("239");
      }
    }, getTestRootDisposable());


    assertFalse(getDomManager().isDomFile(file));
    assertNull(getDomManager().getFileElement(file));

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        file.getDocument().getRootTag().getValue().setText("239");
      }
    }.execute();
    assertTrue(getDomManager().isDomFile(file));
    final DomFileElementImpl<MyElement> root = getDomManager().getFileElement(file);
    assertNotNull(root);
    final MyElement child = root.getRootElement().getChild();
    assertTrue(root.isValid());
    assertTrue(child.isValid());

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        file.getDocument().getRootTag().getValue().setText("57121");
      }
    }.execute();
    assertFalse(getDomManager().isDomFile(file));
    assertNull(getDomManager().getFileElement(file));
    assertFalse(root.isValid());
    assertFalse(child.isValid());
  }

  public void testCopyFileDescriptionFromOriginalFile() throws Throwable {
    final XmlFile file = (XmlFile)createFile("a.xml", "<b>42</b>");

    getDomManager().registerFileDescription(new MockDomFileDescription(MyElement.class, "b", file), getTestRootDisposable());
    file.setName("b.xml");
    assertTrue(getDomManager().isDomFile(file));
    final XmlFile copy = (XmlFile)file.copy();
    assertTrue(getDomManager().isDomFile(copy));
    assertFalse(getDomManager().getFileElement(file).equals(getDomManager().getFileElement(copy)));
  }

  public void testDependantFileDescriptionCauseStackOverflow() throws Throwable {
    final XmlFile interestingFile = (XmlFile)createFile("a.xml", "<b>42</b>");

    getDomManager().registerFileDescription(new MockDomFileDescription(MyElement.class, "b", (XmlFile)null), getTestRootDisposable());
    for (int i = 0; i < 239; i++) {
      getDomManager().registerFileDescription(new MockDomFileDescription(AbstractElement.class, "b", (XmlFile)null) {
        @Override
        @NotNull
        public Set getDependencyItems(final XmlFile file) {
          getDomManager().isDomFile(interestingFile);
          return super.getDependencyItems(file);
        }
      }, getTestRootDisposable());
    }

    getDomManager().isDomFile(interestingFile);
  }

  public void testCheckNamespace() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription(NamespacedElement.class, "xxx", "bar"){

      @Override
      protected void initializeFileDescription() {
        registerNamespacePolicy("foo", "bar");
      }
    }, getTestRootDisposable());

    final PsiFile file = createFile("xxx.xml", "<xxx/>");
    assertFalse(getDomManager().isDomFile(file));

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        ((XmlFile)file).getDocument().getRootTag().setAttribute("xmlns", "bar");
      }
    }.execute();

    assertTrue(getDomManager().isDomFile(file));

  }

  public void testCheckDtdPublicId() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription(NamespacedElement.class, "xxx", "bar"){

      @Override
      protected void initializeFileDescription() {
        registerNamespacePolicy("foo", "bar");
      }
    }, getTestRootDisposable());

    final PsiFile file = createFile("xxx.xml", "<xxx/>");
    assertFalse(getDomManager().isDomFile(file));

    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final Document document = getDocument(file);
        document.insertString(0, "<!DOCTYPE xxx PUBLIC \"bar\" \"http://java.sun.com/dtd/ejb-jar_2_0.dtd\">\n");
        commitDocument(document);
      }
    }.execute();

    assertTrue(getDomManager().isDomFile(file));
  }

  public void testChangeCustomDomness() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription(MyElement.class, "xxx"){
      @Override
      public boolean isMyFile(@NotNull final XmlFile file, @Nullable final Module module) {
        return file.getText().contains("foo");
      }
    }, getTestRootDisposable());
    final XmlFile file = (XmlFile)createFile("xxx.xml", "<xxx zzz=\"foo\"><boy/><boy/><xxx/>");
    final MyElement boy = getDomManager().getFileElement(file, MyElement.class).getRootElement().getBoys().get(0);
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        file.getDocument().getRootTag().setAttribute("zzz", "bar");
      }
    }.execute();
    assertFalse(getDomManager().isDomFile(file));
    assertFalse(boy.isValid());
  }

  public interface AbstractElement extends GenericDomValue<String> {
    GenericAttributeValue<String> getAttr();
  }

  public interface FooElement extends AbstractElement {
  }

  public interface BarElement extends AbstractElement {
  }

  public interface ZipElement extends AbstractElement {
  }

  public interface MyElement extends DomElement {

    MyElement getChild();

    List<MyElement> getBoys();

  }

  @Namespace("foo")
  public interface NamespacedElement extends DomElement {

  }


}
