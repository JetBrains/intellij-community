// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShFunctionResolverInImportedFileTest extends BasePlatformTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("sh") + "/testData/resolve/function_imported_scope");
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
  public void testCaseEight() {
    doNullTest();
  }
  public void testCaseNine() {
    doTest();
  }
  public void testSameFolder() {
    doTest();
  }
  public void testWithParams() {
    doTest();
  }
  public void testCyclicImport() {
    doNullTest();
  }
  public void testCyclicImportCaseTwo() {
    doNullTest();
  }
  public void testTransitiveImport() {
    doTest();
  }
  public void testDotTransitiveImport() {
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
    assertEquals(targetFilePath, targetElement.getContainingFile().getVirtualFile().getName());
  }
}