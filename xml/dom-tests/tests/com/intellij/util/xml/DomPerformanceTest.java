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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class DomPerformanceTest extends DomHardCoreTestCase {
  public void testVisitorPerformance() {
    Ref<MyElement> ref = new Ref<>();

    Benchmark.newBenchmark("creating", () -> ApplicationManager.getApplication().runWriteAction(() -> {
        MyElement element = createElement("<root xmlns=\"http://www.w3.org/1999/xhtml\"/>", MyElement.class);
        MyElement child = element.addChildElement();
        child.getAttr().setValue("239");
        child.getChild239().getAttr().setValue("42");
        child.getChild().getAttr().setValue("42xx");
        child.getChild2().getAttr().setValue("42yy");
        child.addChildElement().getChild().addFooChild().getAttr().setValue("xxx");
        child.addChildElement().addFooChild().getAttr().setValue("yyy");
        child.addChildElement().addFooChild().addBarChild().addBarChild().addChildElement().getChild().getAttr().setValue("xxx");
        child.addChildElement().addBarComposite().setValue("ssss");
        child.addBarChild().getChild2().getAttr().setValue("234178956023");
        for (int i = 0; i < 239; i++) {
          element.addChildElement().copyFrom(child);
        }
        ref.set(element);
    }))
      .startAsSubtest();

    MyElement newElement = createElement(DomUtil.getFile(ref.get()).getText(), MyElement.class);

    Benchmark.newBenchmark("visiting", () ->
      newElement.acceptChildren(new DomElementVisitor() {
        @Override
        public void visitDomElement(DomElement element) {
          element.acceptChildren(this);
        }
      })).startAsSubtest();
  }

  public void testShouldntParseNonDomFiles() throws Throwable {
    for (int i = 0; i < 420; i++) {
      getDomManager().registerFileDescription(new DomFileDescription<>(MyChildElement.class, "foo") {

        @Override
        public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
          fail();
          return super.isMyFile(file, module);
        }
      }, getTestRootDisposable());
      getDomManager().registerFileDescription(new DomFileDescription<>(MyChildElement.class, "bar") {

        @Override
        public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
          fail();
          return super.isMyFile(file, module);
        }
      }, getTestRootDisposable());
    }

    getDomManager().createMockElement(MyChildElement.class, null, true);

    VirtualFile virtualFile = createFile("a.xml", "").getVirtualFile();
    WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Void, IOException>)() -> {
      VfsUtil.saveText(virtualFile, "<root>\n" + StringUtil.repeat("<bar/>\n", 23942) + "</root>");
      return null;
    });

    ((PsiManagerEx)getPsiManager()).cleanupForNextTest();
    final XmlFile file = (XmlFile)getPsiManager().findFile(virtualFile);
    assertFalse(file.getNode().isParsed());
    assertTrue(StringUtil.isNotEmpty(file.getText()));
    Benchmark.newBenchmark("DOM parsing", () -> assertNull(getDomManager().getFileElement(file))).start();
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
