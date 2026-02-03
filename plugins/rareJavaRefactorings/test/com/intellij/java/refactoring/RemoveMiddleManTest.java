// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.removemiddleman.DelegationUtils;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RemoveMiddleManTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("rareJavaRefactorings") + "/testData/removemiddleman/";
  }

  private void doTest(final String conflict) {
    doTest(() -> {
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
        if (conflict != null) TestCase.fail("Conflict expected");
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        if (conflict == null) throw e;
        TestCase.assertEquals(conflict, e.getMessage());
      }
    });
  }

  public void testNoGetter() {
    doTest((String)null);
  }

  public void testSiblings() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  
  public void testInterface() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  public void testPresentGetter() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  public void testInterfaceDelegation() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }
}