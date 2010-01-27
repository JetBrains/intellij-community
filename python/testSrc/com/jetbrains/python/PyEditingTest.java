package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.io.IOException;

/**
 * @author yole
 */
public class PyEditingTest extends PyLightFixtureTestCase {
  public void testNoPairedParenthesesBeforeIdentifier() throws IOException {       // PY-290
    assertEquals("(abc", doTestPairedParentheses("abc", 0));
  }

  public void testPairedParenthesesAtEOF() throws IOException {
    assertEquals("abc()", doTestPairedParentheses("abc", 3));
  }

  private String doTestPairedParentheses(final String text, final int offset) {
    final PsiFile file = ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>() {
      public PsiFile compute() {
        try {
          final PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, text);
          myFixture.getEditor().getCaretModel().moveToOffset(offset);
          myFixture.type('(');
          return file;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    return myFixture.getDocument(file).getText();
  }
}
