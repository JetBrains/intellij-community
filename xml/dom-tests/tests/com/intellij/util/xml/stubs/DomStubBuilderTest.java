// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
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
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/xml/dom-tests/testData/stubs")
public class DomStubBuilderTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @BeforeEach
  void setUp() {
    DomStubTestUtil.registerFooFileDescription(projectFixture.get(), codeInsightFixture.get().getTestRootDisposable());
  }

  @Test
  public void testDomLoading() {
    runInEdtAndWait(() -> getRootStub("foo.xml"));
  }

  @Test
  public void testFoo() {
    doBuilderTest("foo.xml", """
      File:foo
        Element:foo
          Element:id:foo
          Element:list:list0
          Element:list:list1
          Element:bar
            Attribute:string:xxx
            Attribute:int:666
          Element:bar
      """);
  }

  @Test
  public void testFooNoStubbedValueWhenNestedTags() {
    runInEdtAndWait(() -> {
      final ElementStub rootStub = getRootStub("foo.xml");
      assertEquals("", rootStub.getValue());

      final List<? extends Stub> rootChildren = rootStub.getChildrenStubs();
      assertEquals(1, rootChildren.size());
      final Stub fooStub = rootChildren.getFirst();
      final ElementStub fooElementStub = assertInstanceOf(ElementStub.class, fooStub);
      assertEquals("", fooElementStub.getValue());

      final Stub idStub = ContainerUtil.getFirstItem(fooStub.getChildrenStubs());
      final ElementStub idElementStub = assertInstanceOf(ElementStub.class, idStub);
      assertEquals("foo", idElementStub.getValue());
    });
  }

  @Test
  public void testIncompleteAttribute() {
    doBuilderTest("incompleteAttribute.xml", """
      File:foo
        Element:foo
          Element:bar
            Attribute:string:
      """);
  }

  @Test
  public void testDomExtension() {
    DomExtenderEP ep = new DomExtenderEP(Bar.class.getName(), new DefaultPluginDescriptor(PluginId.getId("testDomExtension"), getClass().getClassLoader()));
    ep.domClassName = Bar.class.getName();
    ep.extenderClassName = TestExtender.class.getName();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), DomExtenderEP.EP_NAME, ep, codeInsightFixture.get().getTestRootDisposable());

    doBuilderTest("extender.xml", """
      File:foo
        Element:foo
          Element:bar
            Attribute:extend:xxx
          Element:bar
      """);
  }

  @Test
  public void testNullTag() {
    runInEdtAndWait(() -> {
      Project project = projectFixture.get();
      VirtualFile virtualFile = codeInsightFixture.get().copyFileToProject("nullTag.xml");
      assertNotNull(virtualFile);
      PsiFile psiFile = PsiManagerEx.getInstanceEx(project).getFileManager().findFile(virtualFile);

      StubTreeLoader loader = StubTreeLoader.getInstance();
      VirtualFile file = psiFile.getVirtualFile();
      assertTrue(loader.canHaveStub(file));
      ObjectStubTree stubTree = loader.readFromVFile(project, file);
      assertNotNull(stubTree);
    });
  }

  @Test
  public void testInclusionOnStubs() {
    runInEdtAndWait(() -> doInclusionTest(true));
  }

  @Test
  public void testInclusionOnAST() {
    runInEdtAndWait(() -> doInclusionTest(false));
  }

  private void doInclusionTest(boolean onStubs) {
    CodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.copyFileToProject("include.xml");
    DomStubTestUtil.doBuilderTest("inclusion.xml", """
      File:foo
        Element:foo
          XInclude:href=include.xml xpointer=xpointer(/foo/*)
          Element:bar
            Attribute:string:xxx
            Attribute:int:666
          Element:bar
            XInclude:href=include.xml xpointer=xpointer(/foo/bar-2/*)
      """, fixture);

    PsiFile file = fixture.getFile();
    if (onStubs) {
      GCWatcher.tracking(file.getNode()).ensureCollected();
    }
    assertEquals(!onStubs, ((PsiFileImpl) file).isContentsLoaded());

    DomManager domManager = DomManager.getDomManager(fixture.getProject());
    DomFileElement<Foo> element = domManager.getFileElement((XmlFile)file, Foo.class);
    assert element != null;
    List<Bar> bars = element.getRootElement().getBars();
    assertEquals(3, bars.size());
    assertEquals("included", bars.get(0).getString().getValue());
//    assertEquals("inclusion.xml", bar.getXmlTag().getContainingFile().getName());

    assertEquals(!onStubs, ((PsiFileImpl) file).isContentsLoaded());

    Bar lastBar = bars.get(2);
    List<Bar> lastBarChildren = lastBar.getBars();
    assertEquals(1, lastBarChildren.size());
    assertEquals("included2", lastBarChildren.getFirst().getString().getStringValue());

    XmlTag[] barTags = ((XmlFile)file).getRootTag().findSubTags("bar");
    assertEquals(3, barTags.length);
    for (int i = 1; i < barTags.length; i++) {
      assertEquals(bars.get(i), domManager.getDomElement(barTags[i]), String.valueOf(i));
    }
  }

  private ElementStub getRootStub(@TestDataFile String filePath) {
    return DomStubTestUtil.getRootStub(filePath, codeInsightFixture.get());
  }

  private void doBuilderTest(@TestDataFile String filePath, String stubText) {
    runInEdtAndWait(() -> DomStubTestUtil.doBuilderTest(filePath, stubText, codeInsightFixture.get()));
  }

  public static class TestExtender extends DomExtender<Bar> {

    @Override
    public void registerExtensions(@NotNull Bar bar, @NotNull DomExtensionsRegistrar registrar) {
      registrar.registerAttributeChildExtension(new XmlName("extend"), Custom.class);
    }
  }
}
