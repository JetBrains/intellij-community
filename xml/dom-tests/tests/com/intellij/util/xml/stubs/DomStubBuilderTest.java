// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtenderEP;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xml.stubs.model.Bar;
import com.intellij.util.xml.stubs.model.Custom;
import com.intellij.util.xml.stubs.model.Foo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DomStubBuilderTest extends DomStubTest {

  public void testDomLoading() {
    getRootStub("foo.xml");
  }

  public void testFoo() {
    doBuilderTest("foo.xml", "File:foo\n" +
                             "  Element:foo\n" +
                             "    Element:id:foo\n" +
                             "    Element:list:list0\n" +
                             "    Element:list:list1\n" +
                             "    Element:bar\n" +
                             "      Attribute:string:xxx\n" +
                             "      Attribute:int:666\n" +
                             "    Element:bar\n");
  }

  public void testFooNoStubbedValueWhenNestedTags() {
    final ElementStub rootStub = getRootStub("foo.xml");
    assertEquals("", rootStub.getValue());

    final Stub fooStub = assertOneElement(rootStub.getChildrenStubs());
    final ElementStub fooElementStub = assertInstanceOf(fooStub, ElementStub.class);
    assertEquals("", fooElementStub.getValue());

    final Stub idStub = ContainerUtil.getFirstItem(fooStub.getChildrenStubs());
    final ElementStub idElementStub = assertInstanceOf(idStub, ElementStub.class);
    assertEquals("foo", idElementStub.getValue());
  }

  public void testIncompleteAttribute() {
    doBuilderTest("incompleteAttribute.xml", "File:foo\n" +
                                             "  Element:foo\n" +
                                             "    Element:bar\n" +
                                             "      Attribute:string:\n");
  }

  public void testDomExtension() {
    DomExtenderEP ep = new DomExtenderEP();
    ep.domClassName = Bar.class.getName();
    ep.extenderClassName = TestExtender.class.getName();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), DomExtenderEP.EP_NAME, ep, myFixture.getTestRootDisposable());

    doBuilderTest("extender.xml", "File:foo\n" +
                                  "  Element:foo\n" +
                                  "    Element:bar\n" +
                                  "      Attribute:extend:xxx\n" +
                                  "    Element:bar\n");
  }

  public void testNullTag() {
    VirtualFile virtualFile = myFixture.copyFileToProject("nullTag.xml");
    assertNotNull(virtualFile);
    PsiFile psiFile = ((PsiManagerEx)getPsiManager()).getFileManager().findFile(virtualFile);

    StubTreeLoader loader = StubTreeLoader.getInstance();
    VirtualFile file = psiFile.getVirtualFile();
    assertTrue(loader.canHaveStub(file));
    ObjectStubTree stubTree = loader.readFromVFile(getProject(), file);
    assertNotNull(stubTree);
  }

  public void testInclusionOnStubs() {
    doInclusionTest(true);
  }

  public void testInclusionOnAST() {
    doInclusionTest(false);
  }

  private void doInclusionTest(boolean onStubs) {
    myFixture.copyFileToProject("include.xml");
    doBuilderTest("inclusion.xml", "File:foo\n" +
                                   "  Element:foo\n" +
                                   "    XInclude:href=include.xml xpointer=xpointer(/foo/*)\n" +
                                   "    Element:bar\n" +
                                   "      Attribute:string:xxx\n" +
                                   "      Attribute:int:666\n" +
                                   "    Element:bar\n" +
                                   "      XInclude:href=include.xml xpointer=xpointer(/foo/bar-2/*)\n");

    PsiFile file = myFixture.getFile();
    if (onStubs) {
      GCWatcher.tracking(file.getNode()).tryGc();
    }
    assertEquals(!onStubs, ((PsiFileImpl) file).isContentsLoaded());

    DomManager domManager = DomManager.getDomManager(getProject());
    DomFileElement<Foo> element = domManager.getFileElement((XmlFile)file, Foo.class);
    assert element != null;
    List<Bar> bars = element.getRootElement().getBars();
    assertEquals(3, bars.size());
    assertEquals("included", bars.get(0).getString().getValue());
//    assertEquals("inclusion.xml", bar.getXmlTag().getContainingFile().getName());

    assertEquals(!onStubs, ((PsiFileImpl) file).isContentsLoaded());

    Bar lastBar = bars.get(2);
    assertEquals("included2", assertOneElement(lastBar.getBars()).getString().getStringValue());

    XmlTag[] barTags = ((XmlFile)file).getRootTag().findSubTags("bar");
    assertSize(3, barTags);
    for (int i = 1; i < barTags.length; i++) {
      assertEquals(String.valueOf(i), bars.get(i), domManager.getDomElement(barTags[i]));
    }
  }

  public static class TestExtender extends DomExtender<Bar> {

    @Override
    public void registerExtensions(@NotNull Bar bar, @NotNull DomExtensionsRegistrar registrar) {
      registrar.registerAttributeChildExtension(new XmlName("extend"), Custom.class);
    }
  }
}
