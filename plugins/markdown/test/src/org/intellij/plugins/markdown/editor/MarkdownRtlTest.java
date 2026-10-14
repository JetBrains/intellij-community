package org.intellij.plugins.markdown.editor;

import com.intellij.openapi.editor.impl.AbstractRtlTest;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class MarkdownRtlTest extends AbstractRtlTest {
  private static final String SAMPLE_TEXT = """
    RRRR*R* __RRR__ RR,RR RR-RR RR (RRRR) RR 'RRR' "RR" <RRRRR> [RR]|

    |RRR:|

    * |RRR!|
    * |~RRR~|
    |RRRRRR""";

  @Test
  public void testBasicCase() {
    myFixture.runTest(() -> {
      checkBidiRunBoundaries(SAMPLE_TEXT, "md");
    });
  }
}
