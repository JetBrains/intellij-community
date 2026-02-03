package de.plushnikov.intellij.plugin.configsystem.completion;

import com.intellij.codeInsight.completion.CompletionType;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.hamcrest.CoreMatchers;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit tests for Autocompletion correct logger name with activated config system
 */
public class LoggerCompletionTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/completion";
  }

  public void testLoggerCompletionTest() {
    doTest("LOGGER1");
  }

  private void doTest(String... expectedSuggestions) {
    final String fileName = getTestName(false).replace('$', '/') + ".java";

    myFixture.copyFileToProject("lombok.config", "lombok.config");
    myFixture.configureByFile( fileName);
    myFixture.complete(CompletionType.BASIC, 1);
    List<String> autoSuggestions = myFixture.getLookupElementStrings();
    assertNotNull(autoSuggestions);
    assertThat("Autocomplete doesn't contain right suggestions", autoSuggestions, CoreMatchers.hasItems(expectedSuggestions));
  }
}
