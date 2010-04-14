package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyRenameTest extends PyLightFixtureTestCase {
  public void testRenameField() throws Exception {  // PY-457
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    myFixture.renameElementAtCaret("qu");
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }

  public void testSearchInStrings() throws Exception {  // PY-670
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    final PsiElement element = TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
                                                                                        TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertNotNull(element);
    myFixture.renameElement(element, "bar", true, false);
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }

  public void testRenameParameter() throws Exception {  // PY-385
    myFixture.configureByFile("refactoring/rename/" + getTestName(true) + ".py");
    myFixture.renameElementAtCaret("qu");
    myFixture.checkResultByFile("refactoring/rename/" + getTestName(true) + "_after.py");
  }
}
