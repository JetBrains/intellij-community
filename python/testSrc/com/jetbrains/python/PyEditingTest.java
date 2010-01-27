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
  public void testNoPairedParenthesesBeforeIdentifier() {       // PY-290
    assertEquals("(abc", doTestTyping("abc", 0, '('));
  }

  public void testPairedParenthesesAtEOF() {
    assertEquals("abc()", doTestTyping("abc", 3, '('));
  }

  public void testPairedQuotesInRawString() {   // PY-263
    assertEquals("r''", doTestTyping("r", 1, '\''));
  }

  private String doTestTyping(final String text, final int offset, final char character) {
    final PsiFile file = ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>() {
      public PsiFile compute() {
        try {
          final PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, text);
          myFixture.getEditor().getCaretModel().moveToOffset(offset);
          myFixture.type(character);
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
