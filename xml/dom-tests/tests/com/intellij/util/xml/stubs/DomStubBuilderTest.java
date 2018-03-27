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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
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

    final DomStub fooStub = assertOneElement(rootStub.getChildrenStubs());
    final ElementStub fooElementStub = assertInstanceOf(fooStub, ElementStub.class);
    assertEquals("", fooElementStub.getValue());

    final DomStub idStub = ContainerUtil.getFirstItem(fooStub.getChildrenStubs());
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
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), DomExtenderEP.EP_NAME, ep, myFixture.getTestRootDisposable());

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
    assertNull(stubTree); // no stubs for invalid XML
  }

  public void testInclusion() {
    myFixture.copyFileToProject("include.xml");
    doBuilderTest("inclusion.xml", "File:foo\n" +
                                   "  Element:foo\n" +
                                   "    Element:bar\n" +
                                   "      Attribute:string:xxx\n" +
                                   "      Attribute:int:666\n" +
                                   "    Element:bar\n");

    PsiFile file = myFixture.getFile();
    DomFileElement<Foo> element = DomManager.getDomManager(getProject()).getFileElement((XmlFile)file, Foo.class);
    assert element != null;
    assertEquals(2, element.getRootElement().getBars().size());
  }

  public static class TestExtender extends DomExtender<Bar> {

    @Override
    public void registerExtensions(@NotNull Bar bar, @NotNull DomExtensionsRegistrar registrar) {
      registrar.registerAttributeChildExtension(new XmlName("extend"), Custom.class);
    }
  }
}
