// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.wrapreturnvalue.WrapReturnValueProcessor;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author anna
 */
@TestApplication
public class WrapReturnValueTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile = lightMultiFileFixture(projectFixture, testDataPath());

  private static String testDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//plugins/rareJavaRefactorings:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath() + "/wrapReturnValue/";
    }
    return PluginPathManager.getPluginHomePath("rareJavaRefactorings") + "/testData/wrapReturnValue/";
  }

  @Test
  public void testSimple() { doTest(false); }
  @Test
  public void testGenerics() { doTest(false); }
  @Test
  public void testInconsistentWrapper() { doTest(true, "Existing class does not have getter for selected field"); }
  @Test
  public void testWrapper() { doTest(true); }
  @Test
  public void testStrip() { doTest(true); }
  @Test
  public void testNoConstructor() { doTest(true, "Existing class does not have appropriate constructor"); }
  @Test
  public void testInnerClass() { doTest(false, null, true); }
  @Test
  public void testHierarchy() { doTest(false, null, true); }
  @Test
  public void testAnonymous() { doTest(true, null, false); }
  @Test
  public void testWrongFieldAssignment() { doTest(true, "Existing class does not have appropriate constructor", false); }
  @Test
  public void testInferFieldType() { doTest(true, null, false); }
  @Test
  public void testInferFieldTypeArg() { doTest(true, null, false); }
  @Test
  public void testWrongFieldType() { doTest(true, "Existing class does not have appropriate constructor", false); }
  @Test
  public void testStaticMethodInnerClass() { doTest(false, null, true); }
  @Test
  public void testOpenMethodReference() { doTest(false, null, true); }
  @Test
  public void testRawReturnType() { doTest(true, "Existing class does not have appropriate constructor"); }
  @Test
  public void testReturnInsideLambda() { doTest(false, null, true); }
  @Test
  public void testTypeAnnotations() { doTest(false); }
  @Test
  public void testWithLambdaInside() { doTest(true); }

  private void doTest(final boolean existing) {
    doTest(existing, null);
  }

  private void doTest(final boolean existing, @NonNls String exceptionMessage) {
    doTest(existing, exceptionMessage, false);
  }

  private void doTest(final boolean existing, String exceptionMessage, final boolean createInnerClass) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    try {
      multiFile.get().doTest(() -> {
        PsiClass aClass = myFixture.findClass("Test");
        PsiMethod method = aClass.findMethodsByName("foo", false)[0];
        String wrapperClassName = "Wrapper";
        PsiClass wrapperClass = myFixture.getJavaFacade().findClass(wrapperClassName, GlobalSearchScope.projectScope(myFixture.getProject()));
        assertTrue(!existing || wrapperClass != null);
        PsiField delegateField = existing ? wrapperClass.findFieldByName("myField", false) : null;
        new WrapReturnValueProcessor(wrapperClassName, "", null, method, existing, createInnerClass, delegateField).run();
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (exceptionMessage != null) {
        assertEquals(exceptionMessage, e.getMessage());
        return;
      }
      throw e;
    }
    if (exceptionMessage != null) {
      fail("Conflict was not found");
    }
  }
}