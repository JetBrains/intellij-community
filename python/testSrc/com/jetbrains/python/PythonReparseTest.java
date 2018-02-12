package com.jetbrains.python;

import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.DebugUtil;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * Analogous to CythonReparseTest.
 *
 * @author Mikhail Golubev
 */
public class PythonReparseTest extends PyTestCase {
  private void doTest(final String typedText) {
    final String testName = getTestName(false);
    myFixture.configureByFile("reparse/" + testName + ".py");
    myFixture.type(typedText);
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    final String actualPsiText = DebugUtil.psiToString(myFixture.getFile(), false);
    myFixture.configureByText(testName + ".py", myFixture.getEditor().getDocument().getText());
    final String expectedPsiText = DebugUtil.psiToString(myFixture.getFile(), false);
    assertEquals(expectedPsiText, actualPsiText);
  }

  // PY-15605
  public void testSimilarDecorators() {
    doTest("f");
  }
}
