package de.plushnikov.intellij.plugin.configsystem.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for Autocompletion correct logger name with activated config system
 */
@TestApplication
public class LoggerCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/configsystem/completion");

  @Test
  public void testLoggerCompletionTest() {
    doTest("LOGGER1");
  }

  private void doTest(String... expectedSuggestions) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String fileName = myFixture.getTestName(false).replace('$', '/') + ".java";

    myFixture.copyFileToProject("lombok.config", "lombok.config");
    myFixture.configureByFile( fileName);
    myFixture.complete(CompletionType.BASIC, 1);
    List<String> autoSuggestions = myFixture.getLookupElementStrings();
    assertNotNull(autoSuggestions);
    assertThat("Autocomplete doesn't contain right suggestions", autoSuggestions, CoreMatchers.hasItems(expectedSuggestions));
  }
}
