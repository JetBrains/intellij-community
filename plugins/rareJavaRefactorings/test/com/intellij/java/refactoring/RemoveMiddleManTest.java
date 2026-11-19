// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.removemiddleman.DelegationUtils;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class RemoveMiddleManTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile = lightMultiFileFixture(projectFixture, testDataPath());

  private static String testDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//plugins/rareJavaRefactorings:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath() + "/removemiddleman/";
    }
    return PluginPathManager.getPluginHomePath("rareJavaRefactorings") + "/testData/removemiddleman/";
  }

  private void doTest(final String conflict) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");

      final PsiField field = aClass.findFieldByName("myField", false);
      final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
      List<MemberInfo> infos = new ArrayList<>();
      for (PsiMethod method : methods) {
        final MemberInfo info = new MemberInfo(method);
        info.setChecked(true);
        info.setToAbstract(true);
        infos.add(info);
      }
      try {
        RemoveMiddlemanProcessor processor = new RemoveMiddlemanProcessor(field, infos);
        processor.run();
        if (conflict != null) fail("Conflict expected");
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        if (conflict == null) throw e;
        assertEquals(conflict, e.getMessage());
      }
    });
  }

  @Test
  public void testNoGetter() {
    doTest(null);
  }

  @Test
  public void testSiblings() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  @Test
  public void testInterface() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  @Test
  public void testPresentGetter() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  @Test
  public void testInterfaceDelegation() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }
}