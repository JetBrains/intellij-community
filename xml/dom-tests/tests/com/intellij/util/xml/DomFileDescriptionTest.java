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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.MockDomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DomFileDescriptionTest extends DomHardCoreTestCase {
  private VirtualFile myFooElementFile;
  private VirtualFile myBarElementFile;
  private final Disposable myDisposable = Disposer.newDisposable();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFooElementFile = createFile("a.xml", "<a/>").getVirtualFile();

    getDomManager().registerFileDescription(new MockDomFileDescription<>(FooElement.class, "a", myFooElementFile), myDisposable);

    myBarElementFile = createFile("b.xml", "<b/>").getVirtualFile();

    getDomManager().registerFileDescription(new DomFileDescription<>(BarElement.class, "b") {
      @Override
      public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
        String text = LoadTextUtil.loadText(myFooElementFile).toString();
        return text.contains("239");
      }

      @Override
      public boolean isAutomaticHighlightingEnabled() {
        return false;
      }
    }, myDisposable);

    assertResultsAndClear();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myDisposable);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFooElementFile = null;
      myBarElementFile = null;
      super.tearDown();
    }
  }

  public void testNoInitialDomnessInB() {
    assertFalse(getDomManager().isDomFile(PsiManager.getInstance(myProject).findFile(myBarElementFile)));
    assertNull(getDomManager().getFileElement((XmlFile)PsiManager.getInstance(myProject).findFile(myBarElementFile)));
  }

  public void testIsDomValue() throws Throwable {
    final XmlFile file = (XmlFile)createFile("a.xml", "<b>42</b>");
    getDomManager().registerFileDescription(new DomFileDescription<>(MyElement.class, "b") {

      @Override
      public boolean isMyFile(@NotNull final XmlFile file, final Module module) {
        return /*super.isMyFile(file, module) && */file.getText().contains("239");
      }
    }, myDisposable);


    assertFalse(getDomManager().isDomFile(file));
    assertNull(getDomManager().getFileElement(file));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> file.getDocument().getRootTag().getValue().setText("239"));
    assertTrue(getDomManager().isDomFile(file));
    final DomFileElementImpl<MyElement> root = getDomManager().getFileElement(file);
    assertNotNull(root);
    final MyElement child = root.getRootElement().getChild();
    assertTrue(root.isValid());
    assertTrue(child.isValid());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> file.getDocument().getRootTag().getValue().setText("57121"));
    assertFalse(getDomManager().isDomFile(file));
    assertNull(getDomManager().getFileElement(file));
    assertFalse(root.isValid());
    assertFalse(child.isValid());
  }

  public void testCopyFileDescriptionFromOriginalFile() throws Throwable {
    final XmlFile file = (XmlFile)createFile("a.xml", "<b>42</b>");

    getDomManager().registerFileDescription(new MockDomFileDescription<>(MyElement.class, "b", file.getVirtualFile()), myDisposable);
    ApplicationManager.getApplication().runWriteAction(() -> {
      file.setName("b.xml");
    });

    assertTrue(getDomManager().isDomFile(file));
    final XmlFile copy = (XmlFile)file.copy();
    assertTrue(getDomManager().isDomFile(copy));
    assertFalse(getDomManager().getFileElement(file).equals(getDomManager().getFileElement(copy)));
  }

  public void testDependantFileDescriptionCauseStackOverflow() throws Throwable {
    final XmlFile interestingFile = (XmlFile)createFile("a.xml", "<b>42</b>");

    getDomManager().registerFileDescription(new MockDomFileDescription<>(MyElement.class, "b", null), myDisposable);
    for (int i = 0; i < 239; i++) {
      getDomManager().registerFileDescription(new MockDomFileDescription<>(AbstractElement.class, "b", null) {
        @Override
        @NotNull
        public Set getDependencyItems(final XmlFile file) {
          getDomManager().isDomFile(interestingFile);
          return super.getDependencyItems(file);
        }
      }, myDisposable);
    }

    getDomManager().isDomFile(interestingFile);
  }

  public void testCheckNamespace() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription<>(NamespacedElement.class, "xxx", "bar") {

      @Override
      protected void initializeFileDescription() {
        registerNamespacePolicy("foo", "bar");
      }
    }, myDisposable);

    final PsiFile file = createFile("xxx.xml", "<xxx/>");
    assertFalse(getDomManager().isDomFile(file));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      ((XmlFile)file).getDocument().getRootTag().setAttribute("xmlns", "bar");
    });

    assertTrue(getDomManager().isDomFile(file));
  }

  public void testCheckDtdPublicId() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription<>(NamespacedElement.class, "xxx", "bar") {

      @Override
      protected void initializeFileDescription() {
        registerNamespacePolicy("foo", "bar");
      }
    }, myDisposable);

    final PsiFile file = createFile("xxx.xml", "<xxx/>");
    assertFalse(getDomManager().isDomFile(file));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      final Document document = getDocument(file);
      document.insertString(0, "<!DOCTYPE xxx PUBLIC \"bar\" \"http://java.sun.com/dtd/ejb-jar_2_0.dtd\">\n");
      commitDocument(document);
    });

    assertTrue(getDomManager().isDomFile(file));
  }

  public void testChangeCustomDomness() throws Throwable {
    getDomManager().registerFileDescription(new DomFileDescription<>(MyElement.class, "xxx") {
      @Override
      public boolean isMyFile(@NotNull final XmlFile file, @Nullable final Module module) {
        return file.getText().contains("foo");
      }
    }, myDisposable);
    final XmlFile file = (XmlFile)createFile("xxx.xml", "<xxx zzz=\"foo\"><boy/><boy/><xxx/>");
    final MyElement boy = getDomManager().getFileElement(file, MyElement.class).getRootElement().getBoys().get(0);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      file.getDocument().getRootTag().setAttribute("zzz", "bar");
    });
    assertFalse(getDomManager().isDomFile(file));
    assertFalse(boy.isValid());
  }

  public void testInvalidRootTag() throws Exception {
    final XmlFile file = (XmlFile)createFile("foo.xml", "<a b>");
    DomFileDescription<FooElement> description = new DomFileDescription<>(FooElement.class, "a");
    getDomManager().registerFileDescription(description, myDisposable);
    DomFileElementImpl<FooElement> fileElement = getDomManager().getFileElement(file, FooElement.class);
    assertNotNull(fileElement);
    assertEquals("a", fileElement.getFileDescription().myRootTagName);
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
