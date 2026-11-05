package com.intellij.lang.properties.structuralsearch;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.StructuralSearchTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.structuralsearch.StructuralSearchTestFixtureKt.structuralSearchFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("LanguageMismatch")
@TestApplication
public class PropertiesStructuralSearchTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<StructuralSearchTestFixture> ssrFixture = structuralSearchFixture(projectFixture);

  @Test
  public void testFindComments() {
    String in = """
      # comment
      # another
      x=y
      """;
    findMatchesText(in, "# '_c", "# comment", "# another");
    findMatchesText(in, "# comment", "# comment");
  }

  @Test
  public void testFindPropertyKeys() {
    String in = """
      # comment
      x
      key=value
      """;
    findMatchesText(in, "x", "x");
    findMatchesText(in, "key", "key=value");
    findMatchesText(in, "'_x", "x", "key=value");
  }

  @Test
  public void testFindPropertyValues() {
    String in = """
      # comment
      this.is.a.key=one two three
      """;
    findMatchesText(in, "'_key=one '_x three", "this.is.a.key=one two three");
  }

  private void findMatchesText(@Language("Properties") String in, String pattern, String... expectedResults) {
    ssrFixture.get().findMatchesText(in, pattern, PropertiesFileType.INSTANCE, expectedResults);
  }
}
