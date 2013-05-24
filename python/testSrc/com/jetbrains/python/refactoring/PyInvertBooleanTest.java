package com.jetbrains.python.refactoring;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.invertBoolean.PyInvertBooleanProcessor;

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
    doTest(Lists.<String>newArrayList());
  }

  private void doTest(List<String> files) {
    files.add(0, "refactoring/invertBoolean/" + getTestName(true) + ".before.py");
    myFixture.configureByFiles(files.toArray(new String[files.size()]));
    final PsiElement element = myFixture.getElementAtCaret();
    assertTrue(element instanceof PsiNamedElement);

    final PsiNamedElement target = (PsiNamedElement)element;
    final String name = target.getName();
    assertNotNull(name);
    new PyInvertBooleanProcessor(target, "not"+ StringUtil.toTitleCase(name)).run();
    myFixture.checkResultByFile("refactoring/invertBoolean/" + getTestName(true) + ".after.py");
  }
}
