// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.psi.ShFunctionName;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

public class ShFunctionResolverTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/resolve/";
  }

  public void testSimpleFunction() {
    doTest(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionName.class));
  }

  public void testFromFunction() {
    doTest(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionName.class));
  }

  public void testRecursiveFunction() {
    doTest(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionName.class));
  }

  public void testInnerFunction() {
    doTest(() -> {
      Collection<ShFunctionName> functionNames = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), ShFunctionName.class);
      return ContainerUtil.getLastItem(new ArrayList<>(functionNames));
    });
  }

  public void testFunctionCaseOne() {
    doTest(() -> {
      Collection<ShFunctionName> functionNames = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), ShFunctionName.class);
      return ContainerUtil.getLastItem(new ArrayList<>(functionNames));
    });
  }

  public void testFunctionCaseTwo() {
    doTest(() -> {
      //In this case we assume that functions called from the bottom of the file, if they even don't have a call (to show possible reference)
      Collection<ShFunctionName> functionNames = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), ShFunctionName.class);
      return ContainerUtil.getLastItem(new ArrayList<>(functionNames));
    });
  }

  public void testFunctionCaseThree() {
    doNullTest();
  }

  public void testOuterFunctionCaseOne() {
    doTest(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionName.class));
  }

  public void testOuterFunctionCaseTwo() {
    doNullTest();
  }

  public void testFunctionUnavailable() {
    doNullTest();
  }

  public void testOverridingFunctionCaseOne() {
    doTest(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionName.class));
  }

  public void testOverridingFunctionCaseTwo() {
    doTest(() -> {
      Collection<ShFunctionName> functionNames = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), ShFunctionName.class);
      return ContainerUtil.getLastItem(new ArrayList<>(functionNames));
    });
  }

  public void testOverridingFunctionCaseThree() {
    doTest(() -> {
      Collection<ShFunctionName> functionNames = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), ShFunctionName.class);
      return ContainerUtil.getLastItem(new ArrayList<>(functionNames));
    });
  }

  public void testFunctionCommandSubstitution() {
    doTest(() -> PsiTreeUtil.findChildOfType(myFixture.getFile(), ShFunctionName.class));
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

    assertEquals(expectedTarget.getText(), reference.getElement().getText());

    PsiElement targetElement = reference.resolve();
    assertSame(targetElement, expectedTarget);
  }
}