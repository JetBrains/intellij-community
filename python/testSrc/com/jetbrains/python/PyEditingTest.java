package com.jetbrains.python;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.LogicalPosition;
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

  public void testNonClosingQuoteAtIdent() {   // PY-380
    assertEquals("'abc", doTestTyping("abc", 0, '\''));
  }

  public void testNonClosingQuoteAtNumber() {   // PY-380
    assertEquals("'123", doTestTyping("123", 0, '\''));
  }

  public void testAutoClosingQuoteAtRBracket() {
    assertEquals("'']", doTestTyping("]", 0, '\''));
  }

  public void testAutoClosingQuoteAtRParen() {
    assertEquals("'')", doTestTyping(")", 0, '\''));
  }

  public void testAutoClosingQuoteAtComma() {
    assertEquals("'',", doTestTyping(",", 0, '\''));
  }

  public void testAutoClosingQuoteAtSpace() {
    assertEquals("'' ", doTestTyping(" ", 0, '\''));
  }

  public void testNoClosingTriple() {
    assertEquals("'''", doTestTyping("''", 2, '\''));
  }

  public void testOvertypeFromInside() {
    assertEquals("''", doTestTyping("''", 1, '\''));
  }

  public void testGreedyBackspace() throws Exception {  // PY-254
    myFixture.configureByFile("/editing/py254.py");
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(4, 8));
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      public void run() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
      }
    }, "", null);
    // this should not modify the text, so we can check against the same file 
    myFixture.checkResultByFile("/editing/py254.py", true);
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
