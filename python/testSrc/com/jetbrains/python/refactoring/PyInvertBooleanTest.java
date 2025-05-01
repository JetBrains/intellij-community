// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.actions.InvertBooleanAction;
import com.intellij.refactoring.invertBoolean.InvertBooleanProcessor;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.ArrayUtilRt;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
@TestDataPath("$CONTENT_ROOT/../testData/refactoring/invertBoolean/")
public class PyInvertBooleanTest extends PyTestCase {

  public void testSimple() { doTest(); }

  public void testNegate() { doTest(); }

  public void testParameter() { doTest(); }

  public void testImport() { doTest(Lists.newArrayList("refactoring/invertBoolean/my_file.py")); }

  private void doTest() {
    doTest(new ArrayList<>());
  }

  private void doTest(List<String> files) {
    files.add(0, "refactoring/invertBoolean/" + getTestName(true) + ".before.py");
    myFixture.configureByFiles(ArrayUtilRt.toStringArray(files));
    final PsiElement element = myFixture.getElementAtCaret();
    assertTrue(element instanceof PsiNamedElement);

    final InvertBooleanAction action = new InvertBooleanAction();
    final AnActionEvent event = TestActionEvent.createTestEvent(action);
    ActionUtil.updateAction(action, event);
    assertTrue(event.getPresentation().isEnabledAndVisible());

    final PsiNamedElement target = (PsiNamedElement)element;
    final String name = target.getName();
    assertNotNull(name);
    new InvertBooleanProcessor(target, "not" + StringUtil.toTitleCase(name)).run();
    myFixture.checkResultByFile("refactoring/invertBoolean/" + getTestName(true) + ".after.py");
  }
}
