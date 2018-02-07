/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.codeInsight.regexp.PythonRegexpParserDefinition;
import com.jetbrains.python.codeInsight.regexp.PythonVerboseRegexpLanguage;
import com.jetbrains.python.codeInsight.regexp.PythonVerboseRegexpParserDefinition;
import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyRegexpTest extends PyTestCase {

  public void testLookbehind() {
    doTestHighlighting();
  }

  public void testCountedQuantifier() {
    doTestHighlighting();
  }

  public void testNotEmptyGroup() { // PY-14381
    doTestHighlighting();
  }

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

  public void testDoubleOpenCurly() {  // PY-8252
    doTestHighlighting();
  }

  public void testReSubNotRegexp() {  // PY-11069
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

  public void testParenthesizedStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "re.search((('<caret>foo')), 'foobar')\n",
                       "foo");
  }

  public void testConcatStringRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "re.search('<caret>(.*' + 'bar)' + 'baz', 'foobar')\n",
                       "(.*bar)baz");
  }

  public void testConcatStringWithValuesRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "def f(x, y):\n" +
                       "    re.search('<caret>.*(' + x + ')' + y, 'foo')\n",
                       ".*(missing_value)missing_value");
  }

  public void testPercentFormattingRegexpAutoInjection() {
    doTestInjectedText("import re \n" +
                       "\n" +
                       "def f(x, y):\n" +
                       "    re.search('<caret>.*%s-%d' % (x, y), 'foo')\n",
                       ".*missing_value-missing_value");
  }

  public void testNewStyleFormattingRegexpAutoInjection() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "def f(x, y):\n" +
                       "    re.search('<caret>.*{foo}-{}'.format(x, foo=y), 'foo')\n",
                       ".*missing_value-missing_value");
  }

  public void testNewStyleFormattingEndsWithConstant() {
    doTestInjectedText("import re\n" +
                       "\n" +
                       "def f(**kwargs):" +
                       "    re.search('<caret>(foo{bar}baz$)'.format(**kwargs), 'foo')\n",
                       "(foomissing_valuebaz$)");
  }

  // PY-18881
  public void testVerboseSyntaxWithShortFlag() {
    final PsiElement element =
      doTestInjectedText("import re\n" +
                         "\n" +
                         "re.search(\"\"\"\n" +
                         ".* # <caret>comment\n" +
                         "\"\"\", re.I | re.M | re.X)",
                         "\n.* # comment\n");
    assertEquals(PythonVerboseRegexpLanguage.INSTANCE, element.getLanguage());
  }

  // PY-16404
  public void testFullmatch() {
    doTestInjectedText("import re\n" +
                       "re.fullmatch(\"<caret>\\w+\", \"string\"",
                       "\\w+");
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return getName().equals("testFullmatch") ? ourPy3Descriptor : super.getProjectDescriptor();
  }

  @NotNull
  private PsiElement doTestInjectedText(@NotNull String text, @NotNull String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    assertFalse(files.isEmpty());
    final PsiElement injected = files.get(0).getFirst();
    assertEquals(expected, injected.getText());
    return injected;
  }

  private void doTestHighlighting() {
    myFixture.testHighlighting(true, false, true, "regexp/" + getTestName(true) + ".py");
  }

  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PythonRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest(text, lexer, expectedTokens);
  }
}
