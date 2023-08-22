// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;

public class FormSourceCodeGeneratorTest extends JavaPsiTestCase {
  private VirtualFile myTestProjectRoot;
  private FormSourceCodeGenerator myGenerator;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/sourceCodeGenerator/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myTestProjectRoot = createTestProjectStructure(root);
    myGenerator = new FormSourceCodeGenerator(getProject());
  }

  @Override protected void tearDown() throws Exception {
    myGenerator = null;
    myTestProjectRoot = null;
    super.tearDown();
  }

  public void testSimple() throws IOException { doTest(); }
  public void testCustomCreateComponent() throws IOException { doTest(); }
  public void testCustomComponentReferencedInConstructor() throws IOException { doTest(); }
  public void testMethodCallInConstructor() throws IOException { doTest(); }
  public void testMultipleConstructors() throws IOException { doTest(); }
  public void testConstructorsCallingThis() throws IOException { doTest(); }
  public void testSuperCall() throws IOException { doTest(); }
  public void testDuplicateSetupCall() throws IOException { doTest(); }
  public void testSetupCallWithComments() throws IOException { doTest(); }
  public void testInitializerWithComments() throws IOException { doTest(); }
  public void testTitledBorder() throws IOException { doTest(); }
  public void testBorderNullTitle() throws IOException { doTest(); }
  public void testTitleFromBundle() throws IOException { doTest(); }

  public void testTitledBorderInternal() throws IOException {
    PlatformTestUtil.withSystemProperty(ApplicationManagerEx.IS_INTERNAL_PROPERTY, "true", () -> doTest());
  }

  private void doTest() throws IOException {
    VirtualFile form = myTestProjectRoot.findChild("Test.form");
    assertNotNull(form);
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      try {
        ApplicationManager.getApplication().runWriteAction(() -> myGenerator.generate(form));
      }
      catch (Exception e) {
        fail(e.getMessage());
      }
    }, "", null);

    PsiClass bindingTestClass = myJavaFacade.findClass("BindingTest", ProjectScope.getAllScope(myProject));
    PsiFile psiFile = bindingTestClass.getContainingFile();
    String text = StringUtil.convertLineSeparators(psiFile.getText());
    VirtualFile testAfter = myTestProjectRoot.findChild("BindingTest.java.after");
    String expectedText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(testAfter));
    assertEquals(expectedText, text);
  }
}
