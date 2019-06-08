package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.intellij.plugins.markdown.MarkdownTestingUtil;
import org.owasp.html.PolicyFactory;

import java.io.File;
import java.io.IOException;

public class MarkdownSanitizingTest extends UsefulTestCase {

  private void doTest() {
    final String testDir = MarkdownTestingUtil.TEST_DATA_PATH + "/sanitizing/";
    final String input;
    final String expected;
    try {
      input = FileUtil.loadFile(new File(testDir + getTestName(true) + ".html"));
      final File expectedFile = new File(testDir + getTestName(true) + ".after.html");
      if (expectedFile.exists()) {
        expected = FileUtil.loadFile(expectedFile);
      }
      else {
        expected = input;
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Could not load file", e);
    }

    PolicyFactory sanitizer = MarkdownPreviewFileEditor.SANITIZER_VALUE.getValue();
    assertEquals(expected, sanitizer.sanitize(input));
  }

  public void testEmpty() {
    doTest();
  }

  public void testSample() {
    doTest();
  }

  public void testPuppetApache() {
    doTest();
  }

  public void testTables() {
    doTest();
  }

  public void testBaseUriRelativeRoot() {
    doTest();
  }

  public void testBaseUriFile() {
    doTest();
  }

  public void testImages() {
    doTest();
  }

  public void testCheckboxes() {
    doTest();
  }

  public void testHorizontalRules() {
    doTest();
  }
  public void testStrikeThrough() {
    doTest();
  }
}
