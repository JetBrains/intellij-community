package com.intellij.lang.properties.structuralsearch;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.structuralsearch.StructuralSearchTestCase;
import org.intellij.lang.annotations.Language;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("LanguageMismatch")
public class PropertiesStructuralSearchTest extends StructuralSearchTestCase {

  public void testFindComments() {
    String in = """
      # comment
      # another
      x=y
      """;
    findMatchesText(in, "# '_c", "# comment", "# another");
    findMatchesText(in, "# comment", "# comment");
  }

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

  public void testFindPropertyValues() {
    String in = """
      # comment
      this.is.a.key=one two three
      """;
    findMatchesText(in, "'_key=one '_x three", "this.is.a.key=one two three");
  }

  private void findMatchesText(@Language("Properties") String in, String pattern, String... expectedResults) {
    findMatchesText(in, pattern, PropertiesFileType.INSTANCE, expectedResults);
  }
}
