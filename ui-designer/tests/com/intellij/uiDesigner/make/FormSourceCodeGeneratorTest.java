/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;

/**
 * @author yole
 */
public class FormSourceCodeGeneratorTest extends PsiTestCase {
  private VirtualFile myTestProjectRoot;
  private FormSourceCodeGenerator myGenerator;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = PathManagerEx.getTestDataPath() + "/uiDesigner/sourceCodeGenerator/" + getTestName(true);
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.5"));
            myTestProjectRoot = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
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
    final VirtualFile form = myTestProjectRoot.findChild("test.form");
    assertNotNull(form);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          myGenerator.generate(form);
        }
        catch (Exception e) {
          fail(e.getMessage());
        }
      }
    }, "", null);

    final PsiClass bindingTestClass = myPsiManager.findClass("BindingTest", myProject.getAllScope());
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