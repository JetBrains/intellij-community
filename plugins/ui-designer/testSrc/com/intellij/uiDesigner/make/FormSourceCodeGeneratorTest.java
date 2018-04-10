// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;

/**
 * @author yole
 */
public class FormSourceCodeGeneratorTest extends PsiTestCase {
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

  public void testSimple() throws IOException {
    doTest();
  }

  public void testCustomCreateComponent() throws IOException {
    doTest();
  }

  public void testCustomComponentReferencedInConstructor() throws IOException {
    doTest();
  }

  public void testMethodCallInConstructor() throws IOException {
    doTest();
  }

  public void testMultipleConstructors() throws IOException {
    doTest();
  }

  public void testConstructorsCallingThis() throws IOException {
    doTest();
  }

  public void testSuperCall() throws IOException {
    doTest();
  }

  public void testDuplicateSetupCall() throws IOException {
    doTest();
  }

  private void doTest() throws IOException {
    final VirtualFile form = myTestProjectRoot.findChild("Test.form");
    assertNotNull(form);
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      try {
        ApplicationManager.getApplication().runWriteAction(() -> myGenerator.generate(form));
      }
      catch (Exception e) {
        fail(e.getMessage());
      }
    }, "", null);

    final PsiClass bindingTestClass = myJavaFacade.findClass("BindingTest", ProjectScope.getAllScope(myProject));
    assertNotNull(bindingTestClass);
    final VirtualFile testAfter = myTestProjectRoot.findChild("BindingTest.java.after");
    assertNotNull(testAfter);
    String expectedText = StringUtil.convertLineSeparators(VfsUtil.loadText(testAfter));
    final PsiFile psiFile = bindingTestClass.getContainingFile();
    assertNotNull(psiFile);
    final String text = StringUtil.convertLineSeparators(psiFile.getText());
    assertEquals(expectedText, text);
  }
}
