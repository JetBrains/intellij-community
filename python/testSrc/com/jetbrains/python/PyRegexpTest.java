package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.codeInsight.regexp.PythonRegexpParserDefinition;
import com.jetbrains.python.codeInsight.regexp.PythonVerboseRegexpParserDefinition;
import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyRegexpTest extends PyTestCase {
  public void testNestedCharacterClasses() {  // PY-2908
    doTestHighlighting();
  }

  public void testNestedCharacterClassesLexer() {
    doTestLexer("[][]", "CLASS_BEGIN", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testNestedCharacterClasses2() {  // PY-2908
    doTestHighlighting();
  }

  public void testOctal() {  // PY-2906
    doTestHighlighting();
  }

  public void testBraceInPythonCharacterClass() {  // PY-1929
    doTestHighlighting();
  }

  public void testNegatedBraceInCharacterClass() {
    doTestLexer("[^][]", "CLASS_BEGIN", "CARET", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testDanglingMetacharacters() {  // PY-2430
    doTestHighlighting();
  }

  public void testVerbose() {
    Lexer lexer = new PythonVerboseRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest("# abc", lexer, "COMMENT", "COMMENT");
  }

  public void testRedundantEscapeSingleQuote() {  // PY-5027
    doTestHighlighting();
  }

  public void testBraceCommaN() {  // PY-8304
    doTestHighlighting();
  }

  public void testVerboseAsKwArg() {  // PY-8143
    doTestHighlighting();
  }

  public void testVerboseEscapedHash() {  // PY-6545
    doTestHighlighting();
  }

  public void _testDoubleOpenCurly() {  // PY-8252
    doTestHighlighting();
  }

  public void testSingleStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                        "\n" +
                        "re.search('<caret>.*bar',\n" +
                        "          'foobar')\n",
                       ".*bar");
  }

  // PY-11057
  public void testAdjacentStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                        "\n" +
                        "re.search('<caret>.*()'\n" +
                        "          'abc',\n" +
                        "          'foo')\n",
                       ".*()abc");
  }

  private void doTestInjectedText(@NotNull String text, @NotNull String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myFixture.getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    assertFalse(files.isEmpty());
    final PsiElement injected = files.get(0).getFirst();
    assertEquals(expected, injected.getText());
  }

  private void doTestHighlighting() {
    myFixture.testHighlighting(true, false, true, "regexp/" + getTestName(true) + ".py");
  }

  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PythonRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest(text, lexer, expectedTokens);
  }
}
