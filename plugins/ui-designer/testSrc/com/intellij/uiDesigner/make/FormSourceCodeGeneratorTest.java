// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class FormSourceCodeGeneratorTest extends JavaPsiTestCase {
  private VirtualFile myTestProjectRoot;
  private String testDataRoot;
  private FormSourceCodeGenerator myGenerator;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    testDataRoot = PluginPathManager.getPluginHomePath("ui-designer") + "/testData/sourceCodeGenerator/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myTestProjectRoot = createTestProjectStructure(testDataRoot);
    myGenerator = new FormSourceCodeGenerator(getProject(), false);
  }

  @Override protected void tearDown() throws Exception {
    myGenerator = null;
    myTestProjectRoot = null;
    super.tearDown();
  }

  public void testSimple() { doTest(); }
  public void testCustomCreateComponent() { doTest(); }
  public void testCustomComponentReferencedInConstructor() { doTest(); }
  public void testMethodCallInConstructor() { doTest(); }
  public void testMultipleConstructors() { doTest(); }
  public void testConstructorsCallingThis() { doTest(); }
  public void testSuperCall() { doTest(); }
  public void testDuplicateSetupCall() { doTest(); }
  public void testSetupCallWithComments() { doTest(); }
  public void testInitializerWithComments() { doTest(); }
  public void testTitledBorder() { doTest(); }
  public void testBorderNullTitle() { doTest(); }
  public void testTitleFromBundle() { doTest(); }

  public void testTitledBorderInternal() {
    PlatformTestUtil.withSystemProperty(ApplicationManagerEx.IS_INTERNAL_PROPERTY, "true", () -> doTest());
  }

  private void doTest() {
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

    if (!myGenerator.getErrors().isEmpty()) {
      fail(myGenerator.getErrors().stream().map(x -> x.getErrorMessage()).collect(Collectors.joining("\n")));
    }

    PsiClass bindingTestClass = myJavaFacade.findClass("BindingTest", ProjectScope.getAllScope(myProject));
    PsiFile psiFile = bindingTestClass.getContainingFile();

    PsiTestUtil.checkFileStructure(psiFile);

    String text = StringUtil.convertLineSeparators(psiFile.getText());

    Path expectedFile = Path.of(testDataRoot, "BindingTest.java.after");
    assertTrue("Expected file is supposed to be on disk: " + expectedFile, Files.isRegularFile(expectedFile));

    assertSameLinesWithFile(expectedFile.toString(), text);
  }
}
