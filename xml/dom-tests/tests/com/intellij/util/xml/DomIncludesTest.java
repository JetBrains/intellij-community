/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.Timings;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.Consumer;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.DomManagerImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author peter
 */
public class DomIncludesTest extends JavaCodeInsightFixtureTestCase {

  public void testGetChildrenHonorsIncludes() throws Throwable {
    final MyElement rootElement = createDomFile("a.xml", "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">" +
                            "<xi:include href=\"b.xml\" xpointer=\"xpointer(/xxx/*)\"/>" +
                            "<child xxx=\"a\"/>" +
                            "</root>");
    createFile("b.xml", "<xxx><child xxx=\"b\"/></xxx>");
    final List<DomIncludesTest.Child> children = rootElement.getChildren();
    Consumer<MyElement> consumer1 = new Consumer<MyElement>() {
      @Override
      public void consume(final MyElement element) {
        assertFalse(element.getXmlElement().isPhysical());
        assertEquals(rootElement, element.getParent());
        assertEquals(DomUtil.getFileElement(rootElement), DomUtil.getFileElement(element));
        assertEquals("b", element.getXxx().getValue());
      }
    };
    Consumer<MyElement> consumer2 = new Consumer<MyElement>() {
      @Override
      public void consume(final MyElement element) {
        assertTrue(element.getXmlElement().isPhysical());
        assertEquals(rootElement, element.getParent());
        assertEquals(DomUtil.getFileElement(rootElement), DomUtil.getFileElement(element));
        assertEquals("a", element.getXxx().getValue());
      }
    };
    assertOrderedCollection(children, new Consumer[] { consumer1, consumer2 });
  }

  public void testNamespaces() throws Throwable {
    final MyElement rootElement = createDomFile("a.xml", "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\" xmlns:foo=\"foo\">" +
                            "<xi:include href=\"b.xml\" xpointer=\"xpointer(/xxx/*)\"/>" +
                            "<foo:boy xxx=\"a\"/>" +
                            "</root>");
    DomUtil.getFileElement(rootElement).getFileDescription().registerNamespacePolicy("foo", "foo");

    createFile("b.xml", "<xxx xmlns:foo=\"foo\"><foo:boy xxx=\"b\"/></xxx>");
    final List<Boy> children = rootElement.getBoys();
    assertOrderedCollection(children, new Consumer<Boy>() {
      @Override
      public void consume(final Boy element) {
        assertFalse(element.getXmlElement().isPhysical());
        assertEquals(rootElement, element.getParent());
        assertEquals(DomUtil.getFileElement(rootElement), DomUtil.getFileElement(element));
        assertEquals("b", element.getXxx().getValue());
      }
    }, new Consumer<Boy>() {
      @Override
      public void consume(final Boy element) {
        assertTrue(element.getXmlElement().isPhysical());
        assertEquals(rootElement, element.getParent());
        assertEquals(DomUtil.getFileElement(rootElement), DomUtil.getFileElement(element));
        assertEquals("a", element.getXxx().getValue());
      }
    });
  }

  public void testEqualsWithMultiThreadedIncludes() throws Throwable {
    final MyElement rootElement = createDomFile("a.xml", "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">" +
                            "<xi:include href=\"b.xml\" xpointer=\"xpointer(/xxx/*)\"/>" +
                            "</root>");

    final String textB =
      "<xxx><boy/><xi:include href=\"c.xml\" xpointer=\"xpointer(/xxx/*)\"/><child/><xi:include href=\"c.xml\" xpointer=\"xpointer(/xxx/*)\"/></xxx>";
    final PsiFile fileB = createFile("b.xml", textB);
    final String textC =
      "<xxx><child/><xi:include href=\"d.xml\" xpointer=\"xpointer(/xxx/*)\"/><boy/><xi:include href=\"d.xml\" xpointer=\"xpointer(/xxx/*)\"/></xxx>";
    final PsiFile fileC = createFile("c.xml", textC);
    final String textD =
      "<xxx><boy/><xi:include href=\"e.xml\" xpointer=\"xpointer(/xxx/*)\"/><child/><xi:include href=\"e.xml\" xpointer=\"xpointer(/xxx/*)\"/></xxx>";
    final PsiFile fileD = createFile("d.xml", textD);
    final String textE = "<xxx><boy/><child/><boy/><child/><boy/><child/><boy/><child/><boy/><child/><boy/><child/></xxx>";
    final PsiFile fileE = createFile("e.xml", textE);
    final int threadCount = 100;
    final int iterationCount = Timings.adjustAccordingToMySpeed(100, true);
    System.out.println("iterationCount = " + iterationCount);

    final CountDownLatch finished = new CountDownLatch(threadCount);
    final AtomicReference<Exception> ex = new AtomicReference<Exception>();

    for (int j = 0; j < threadCount; j++) {
      JobLauncher.getInstance().submitToJobThread(0, new Runnable() {
        @Override
        public void run() {
          try {
          for (int k = 0; k < iterationCount; k++) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                  final List<Boy> boys = rootElement.getBoys();
                  Thread.yield();
                  final List<Child> children = rootElement.getChildren();
                  Thread.yield();
                  assertEquals(boys, rootElement.getBoys());
                  assertEquals(children, rootElement.getChildren());
              }
            });
            Thread.yield();
          }
          }
          catch (Exception e) {
            ex.set(e);
          }
          finally {
            finished.countDown();
          }
        }
      });
    }

    for (int i = 0; i < iterationCount; i++) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fileB.getViewProvider().getDocument().insertString(0, " ");
          fileD.getViewProvider().getDocument().insertString(0, " ");
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); //clear xinclude caches
        }
      });
      Thread.sleep(10);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fileC.getViewProvider().getDocument().insertString(0, " ");
          fileE.getViewProvider().getDocument().insertString(0, " ");
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); //clear xinclude caches
        }
      });
      Thread.sleep(10);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          fileB.getViewProvider().getDocument().setText(textB);
          fileC.getViewProvider().getDocument().setText(textC);
          fileD.getViewProvider().getDocument().setText(textD);
          fileE.getViewProvider().getDocument().setText(textE);
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); //clear xinclude caches
        }
      });
    }

    finished.await();
    final Exception exception = ex.get();
    if (exception != null) {
      throw exception;
    }
  }

  public void testNavigationToIncluded() throws Throwable {
    final MyElement rootElement = createDomFile("a.xml", "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">" +
                            "<xi:include href=\"b.xml\" xpointer=\"xpointer(/xxx/*)\"/>" +
                            "<child ref=\"b\"/>" +
                            "</root>");
    final XmlFile includedFile = (XmlFile) createFile("b.xml", "<xxx><child xxx=\"b\"/></xxx>");
    final List<Child> children = rootElement.getChildren();
    final MyElement domTarget = children.get(0);
    final GenericAttributeValue<Child> ref = children.get(1).getRef();
    final MyElement value = ref.getValue();
    assertEquals(domTarget, value);

    myFixture.configureFromTempProjectFile("a.xml");

    final int offset = ref.getXmlAttributeValue().getTextRange().getStartOffset() + 1;
    myFixture.getEditor().getCaretModel().moveToOffset(offset);
    final PsiElement target = GotoDeclarationAction.findTargetElement(getProject(), myFixture.getEditor(), offset);
    PsiElement element = ((DomTarget)((PomTargetPsiElement)target).getTarget()).getNavigationElement();
//    assertSame(PomService.convertToPsi(DomTarget.getTarget(domTarget)), target);
    assertSame(includedFile.getDocument().getRootTag().getSubTags()[0].getAttributes()[0].getValueElement(), element);
  }

  private MyElement createDomFile(final String fileName, final String fileText) throws IOException {
    final XmlFile xmlFile = (XmlFile) createFile(fileName, fileText);
    final DomFileElementImpl<MyElement> element = getDomManager().getFileElement(xmlFile, MyElement.class, xmlFile.getDocument().getRootTag().getName());
    return element.getRootElement();
  }

  private PsiFile createFile(final String fileName, final String fileText) throws IOException {
    final VirtualFile file = myFixture.getTempDirFixture().createFile(fileName);
    VfsUtil.saveText(file, fileText);
    return getPsiManager().findFile(file);
  }

  private DomManagerImpl getDomManager() {
    return DomManagerImpl.getDomManager(getProject());
  }

  public interface MyElement extends DomElement {

    List<Child> getChildren();

    List<Boy> getBoys();

    @NameValue
    GenericAttributeValue<String> getXxx();

    GenericAttributeValue<Child> getRef();

  }

  @Namespace("foo")
  public interface Boy extends MyElement {

  }

  public interface Child extends MyElement {

  }

}
