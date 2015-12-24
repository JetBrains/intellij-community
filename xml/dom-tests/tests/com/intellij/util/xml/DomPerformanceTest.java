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
package com.intellij.util.xml;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.xml.impl.dtd.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.List;

/**
 * @author peter
 */
public class DomPerformanceTest extends DomHardCoreTestCase{

  public void testVisitorPerformance() throws Throwable {
    final MyElement element = createElement("<root xmlns=\"adsf\" targetNamespace=\"adsf\"/>", MyElement.class);

    MetaRegistry.bindDataToElement(DomUtil.getFile(element).getDocument(), new XmlNSDescriptorImpl());

    final MyElement child = element.addChildElement();
    child.getAttr().setValue("239");
    child.getChild239().getAttr().setValue("42");
    child.getChild().getAttr().setValue("42xx");
    child.getChild2().getAttr().setValue("42yy");
    child.addChildElement().getChild().addFooChild().getAttr().setValue("xxx");
    child.addChildElement().addFooChild().getAttr().setValue("yyy");
    child.addChildElement().addFooChild().addBarChild().addBarChild().addChildElement().getChild().getAttr().setValue("xxx");
    child.addChildElement().addBarComposite().setValue("ssss");
    child.addBarChild().getChild2().getAttr().setValue("234178956023");

    PlatformTestUtil.startPerformanceTest(getTestName(false), 80000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            for (int i = 0; i < 239; i++) {
              element.addChildElement().copyFrom(child);
            }
          }
        });
      }
    }).cpuBound().attempts(1).useLegacyScaling().assertTiming();

    final MyElement newElement = createElement(DomUtil.getFile(element).getText(), MyElement.class);

    PlatformTestUtil.startPerformanceTest(getTestName(false), 300, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        newElement.acceptChildren(new DomElementVisitor() {
          @Override
          public void visitDomElement(DomElement element) {
            element.acceptChildren(this);
          }
        });

      }
    }).cpuBound().useLegacyScaling().assertTiming();
  }

  public void testShouldntParseNonDomFiles() throws Throwable {
    for (int i = 0; i < 420; i++) {
      getDomManager().registerFileDescription(new DomFileDescription(MyChildElement.class, "foo") {

        @Override
        public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
          fail();
          return super.isMyFile(file, module);
        }
      }, getTestRootDisposable());
      getDomManager().registerFileDescription(new DomFileDescription(MyChildElement.class, "bar") {

        @Override
        public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
          fail();
          return super.isMyFile(file, module);
        }
      }, getTestRootDisposable());
    }

    getDomManager().createMockElement(MyChildElement.class, null, true);

    @NotNull final VirtualFile virtualFile = createFile("a.xml", "").getVirtualFile();
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {

        final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(virtualFile.getOutputStream(this)));
        writer.write("<root>\n");
        for (int i = 0; i < 23942; i++) {
          writer.write("<bar/>\n");
        }
        writer.write("</root>");
        writer.close();
        virtualFile.refresh(false, false);
      }
    }.execute();

    ((PsiManagerImpl)getPsiManager()).getFileManager().cleanupForNextTest();
    final XmlFile file = (XmlFile)getPsiManager().findFile(virtualFile);
    assertFalse(file.getNode().isParsed());
    assertTrue(StringUtil.isNotEmpty(file.getText()));
    PlatformTestUtil.startPerformanceTest("", 100, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        assertNull(getDomManager().getFileElement(file));
      }
    }).cpuBound().useLegacyScaling().assertTiming();
  }

  public void testDontParseNamespacedDomFiles() throws Exception {
    getDomManager().registerFileDescription(new DomFileDescription(MyNamespacedElement.class, "foo") {
      @Override
      protected void initializeFileDescription() {
        registerNamespacePolicy("project", "project");
      }
    }, getTestRootDisposable());

    XmlFile file = (XmlFile)createFile("a.xml", "<foo xmlns=\"project\"/>");
    assertFalse(file.getNode().isParsed());
    assertNotNull(DomManager.getDomManager(getProject()).getFileElement(file, MyNamespacedElement.class));

    file = (XmlFile)createFile("a.xml", "<foo xmlns=\"project2\"/>");
    assertFalse(file.getNode().isParsed());
    assertNull(DomManager.getDomManager(getProject()).getFileElement(file, MyNamespacedElement.class));
  }

  @Namespace("project")
  public interface MyNamespacedElement extends DomElement {

  }
  public interface MyChildElement extends DomElement {
    @Attribute
    @Required
    GenericAttributeValue<String> getAttr();

    List<MyFooConcreteElement> getFooChildren();

    MyFooConcreteElement addFooChild();
  }

  public interface MyElement extends DomElement {
    @Attribute
    @Required
    GenericAttributeValue<String> getAttr();

    String getValue();

    void setValue(String s);

    MyChildElement getChild();

    @SubTag(value = "child", index = 1)
    MyChildElement getChild2();

    MyChildElement getChild239();

    List<MyElement> getChildElements();

    MyElement addChildElement();

    List<MyAbstractElement> getAbstractElements();

    @SubTagList("abstract-element")
    MyBarConcreteElement addBarChild();

    @SubTagList("abstract-element")
    MyFooConcreteElement addFooChild();

    @SubTagsList({"child-element", "abstract-element"})
    List<MyElement> getCompositeList();

    @SubTagsList(value = {"child-element", "abstract-element"}, tagName = "abstract-element")
    MyBarConcreteElement addBarComposite();

  }

  public interface MyAbstractElement extends MyElement {
  }

  public interface MyFooConcreteElement extends MyAbstractElement {
  }

  public interface MyBarConcreteElement extends MyAbstractElement {
  }


}
