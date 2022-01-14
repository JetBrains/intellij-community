// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

public class ShFunctionResolverTest extends BasePlatformTestCase {
  private final Supplier<PsiElement> getLastFunction = () -> {
    Collection<ShFunctionDefinition> functionDefinitions = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), ShFunctionDefinition.class);
    return ContainerUtil.getLastItem(new ArrayList<>(functionDefinitions));
  };
  private final Supplier<PsiElement> getFirstFunction = () -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionDefinition.class);

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/resolve/function_file_scope";
  }

  public void testSimpleFunction() {
    doTest(getFirstFunction);
  }

  public void testFromFunction() {
    doTest(getFirstFunction);
  }

  public void testRecursiveFunction() {
    doTest(getFirstFunction);
  }

  public void testInnerFunction() {
    doTest(getLastFunction);
  }

  public void testFunctionCaseOne() {
    doNullTest();
  }

  public void testFunctionCaseTwo() {
    doNullTest();
  }

  public void testFunctionCaseThree() {
    doNullTest();
  }

  public void testOuterFunctionCaseOne() {
    doTest(getFirstFunction);
  }

  public void testOuterFunctionCaseTwo() {
    doNullTest();
  }

  public void testFunctionUnavailable() {
    doNullTest();
  }

  public void testOverridingFunctionCaseOne() {
    doTest(getFirstFunction);
  }

  public void testOverridingFunctionCaseTwo() {
    doTest(getLastFunction);
  }

  public void testOverridingFunctionCaseThree() {
    doTest(getLastFunction);
  }

  public void testOverridingFunctionCaseFour() {
    doTest(getFirstFunction);
  }

  public void testOverridingFunctionCaseFive() {
    doTest(getLastFunction);
  }

  public void testFunctionCommandSubstitution() {
    doTest(getFirstFunction);
  }

  public void testCaseOne() {
    doNullTest();
  }

  public void testCaseTwo() {
    doNullTest();
  }

  public void testCaseThree() {
    doNullTest();
  }

  public void testCaseFour() {
    doNullTest();
  }

  public void testCaseFive() {
    doTest(getFirstFunction);
  }

  public void testCaseSix() {
    doNullTest();
  }

  public void testCaseSeven() {
    doNullTest();
  }

  private void configFile() {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".sh");
  }

  private void doNullTest() {
    configFile();
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    assertNull(reference.resolve());
  }

  private void doTest(Supplier<PsiElement> supplier) {
    configFile();

    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    PsiElement expectedTarget = supplier.get();
    assertNotNull(expectedTarget);

    assertEquals(expectedTarget.getText(), reference.resolve().getText());

    PsiElement targetElement = reference.resolve();
    assertSame(expectedTarget, targetElement);
  }
}