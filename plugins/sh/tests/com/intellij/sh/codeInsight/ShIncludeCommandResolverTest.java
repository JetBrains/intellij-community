// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShIncludeCommandResolverTest extends BasePlatformTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("sh") + "/testData/resolve/include_command");
    myFixture.copyDirectoryToProject(getTestName(true), "");
  }

  public void testCaseOne() {
    doTest();
  }
  public void testCaseTwo() {
    doTest();
  }
  public void testCaseThree() {
    doTest();
  }
  public void testCaseFour() {
    doTest();
  }
  public void testCaseFive() {
    doTest();
  }
  public void testCaseSix() {
    doTest("./parent folder/source.sh");
  }
  public void testCaseNine() {
    doTest();
  }
  public void testSameFile() {
    doTest("source.sh", "source.sh");
  }
  public void testSameFolder() {
    doTest();
  }
  public void testWithParams() {
    doTest();
  }
  public void testWithParamsCaseTwo() {
    doNullTest();
  }
  public void testDotImport() {
    doTest();
  }

  private void doNullTest() {
    myFixture.configureByFile("source.sh");
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    assertNull(reference.resolve());
  }

  private void doTest() {
    doTest( "source.sh", "target.sh");
  }

  private void doTest(String filePath) {
    doTest( filePath, "target.sh");
  }

  private void doTest(String filePath, String targetFilePath) {
    myFixture.configureByFile(filePath);
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    PsiElement targetElement = reference.resolve();
    assertNotNull(targetElement);
    assertTrue(targetElement instanceof PsiFile);
    assertEquals(targetFilePath, ((PsiFile)targetElement).getVirtualFile().getName());
  }
}