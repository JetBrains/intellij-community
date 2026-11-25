package org.intellij.plugins.markdown.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class MarkdownQuoteHandlerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void doTest(@NotNull String text, char charToType, @NotNull String expectedResult) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.configureByText("test.md", text);
    assertInstanceOf(MarkdownFile.class, file);

    myFixture.type(charToType);
    myFixture.checkResult(expectedResult);
  }

  @Test
  public void testSingleQuote() {
    doTest("Hello <caret> world", '\'', "Hello '<caret>' world");
  }

  @Test
  public void testSingleQuoteBeforeWord() {
    doTest("Hello <caret>world", '\'', "Hello '<caret>world");
  }

  @Test
  public void testSingleQuoteAtEof() {
    doTest("Hello <caret>", '\'', "Hello '<caret>'");
  }

  @Test
  public void testSingleQuoteInsideParentheses() {
    doTest("(sometext <caret>)", '\'', "(sometext '<caret>')");
  }

  @Test
  public void testSecondSingleQuoteInsideParentheses() {
    doTest("(sometext '<caret>)", '\'', "(sometext ''<caret>)");
  }

  @Test
  public void testDeleteMiddleSingleQuoteInsideParentheses() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("test.md", "(sometext ''<caret>')");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.checkResult("(sometext '<caret>')");
  }

  @Test
  public void testDoubleQuote() {
    doTest("Hello <caret> world", '"', "Hello \"<caret>\" world");
  }

  @Test
  public void testBacktick() {
    doTest("Hello <caret> world", '`', "Hello `<caret>` world");
  }

  @Test
  public void testBacktickAfterOpeningParenthesis() {
    doTest("Test (<caret>", '`', "Test (`<caret>`");
  }

  @Test
  public void testBacktickInsideParenthesis() {
    doTest("Test (<caret>)", '`', "Test (`<caret>`)");
  }

  @Test
  public void testSecondBacktickInsideParentheses() {
    doTest("(sometext `<caret>)", '`', "(sometext ``<caret>)");
  }

  @Test
  public void testDeleteMiddleBacktickInsideParentheses() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("test.md", "(sometext ``<caret>`)");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.checkResult("(sometext `<caret>`)");
  }

  @Test
  public void testEmphAsterisk() {
    doTest("Hello <caret> world", '*', "Hello *<caret> world");
  }

  @Test
  public void testEmphUnderscore() {
    doTest("Hello <caret> world", '_', "Hello _<caret> world");
  }

  @Test
  public void testSingleQuoteAsApostrophe() {
    doTest("Hello dear<caret> world", '\'', "Hello dear'<caret> world");
  }

  @Test
  public void testBacktickAsAccent() {
    doTest("Hello dear<caret> world", '`', "Hello dear`<caret> world");
  }

  @Test
  public void testClosingQuote() {
    doTest("Hello '<caret>' world", '\'', "Hello ''<caret> world");
  }

  @Test
  public void testClosingQuoteWithWord() {
    doTest("Hello 'cool<caret>' world", '\'', "Hello 'cool'<caret> world");
  }

  @Test
  public void testBacktickShouldBeAdded() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.configureByText("test.md", "Hello <caret> world");
    assertInstanceOf(MarkdownFile.class, file);

    myFixture.type('`');
    myFixture.checkResult("Hello `<caret>` world");
    myFixture.type('`');
    myFixture.checkResult("Hello ``<caret>`` world");
    myFixture.type('`');
    myFixture.checkResult("Hello ```<caret>``` world");
  }

  @Test
  public void testBacktickShouldBeAddedStartOfLine() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.configureByText("test.md", "<caret>");
    assertInstanceOf(MarkdownFile.class, file);

    myFixture.type('`');
    myFixture.checkResult("`<caret>`");
    myFixture.type('`');
    myFixture.checkResult("``<caret>``");
    myFixture.type('`');
    myFixture.checkResult("```<caret>```");
  }

  @Test
  public void testBacktickShouldBeAddedAfterClosedCodeSpan() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.configureByText("test.md", "`ff` <caret>");
    assertInstanceOf(MarkdownFile.class, file);

    myFixture.type('`');
    myFixture.checkResult("`ff` `<caret>`");
    myFixture.type('`');
    myFixture.checkResult("`ff` ``<caret>``");
  }

  @Test
  public void testBacktickShouldCloseUnclosedCodeSpan() {
    doTest("`ff <caret>", '`', "`ff `<caret>");
  }

  @Test
  public void testBackticksGoodBalance() {
    doTest("Hello ``code<caret>`` world", '`', "Hello ``code`<caret>` world");
  }

  @Test
  public void testBackticksGoodBalance2() {
    doTest("Hello ``code`<caret>` world", '`', "Hello ``code``<caret> world");
  }

  @Test
  public void testBackticksBadBalance() {
    doTest("Hello ``code<caret>` world", '`', "Hello ``code`<caret>` world");
  }

  @Test
  public void testBracketOvertypeInCheckbox() { doTest("- [ <caret>]", ']', "- [ ]<caret>"); }

  @Test
  public void testDoubleQuoteInsideBraces() {
    doTest("{<caret>}", '"', "{\"<caret>\"}");
  }

  @Test
  public void testClosingDoubleQuoteInsideBraces() {
    doTest("{\"<caret>\"}", '"', "{\"\"<caret>}");
  }

  @Test
  public void testDoubleQuoteAttributeValue() {
    doTest("{ key=<caret> }", '"', "{ key=\"<caret>\" }");
  }

  @Test
  public void testDoubleQuoteAttributeValueBeforeBrace() {
    doTest("{ key=<caret>}", '"', "{ key=\"<caret>\"}");
  }

  @Test
  public void testDoubleQuoteSecondAttributeValue() {
    doTest("{ id=\"x\" key=<caret> }", '"', "{ id=\"x\" key=\"<caret>\" }");
  }

  @Test
  public void testClosingDoubleQuoteAttributeValue() {
    doTest("{ key=\"value<caret>\" }", '"', "{ key=\"value\"<caret> }");
  }

  @Test
  public void testSingleQuoteAttributeValue() {
    doTest("{ key=<caret> }", '\'', "{ key='<caret>' }");
  }

  @Test
  public void testSingleQuoteAfterOpeningParenthesis() {
    doTest("Test (<caret>)", '\'', "Test ('<caret>')");
  }

  @Test
  public void testSingleQuoteInsideBrackets() {
    doTest("Test [<caret>]", '\'', "Test ['<caret>']");
  }

  @Test
  public void testSingleQuoteAfterSpaceInsideBraces() {
    doTest("{ <caret>}", '\'', "{ '<caret>'}");
  }

  @Test
  public void testBacktickInsideBraces() {
    doTest("{<caret>}", '`', "{`<caret>`}");
  }

  @Test
  public void testSingleQuoteInsideBraces() {
    doTest("{<caret>}", '\'', "{'<caret>'}");
  }

  @Test
  public void testSecondSingleQuoteInsideBraces() {
    doTest("{'<caret>}", '\'', "{''<caret>}");
  }

  @Test
  public void testSingleQuoteInsideBracesAfterText() {
    doTest("text {<caret>}", '\'', "text {'<caret>'}");
  }
}
