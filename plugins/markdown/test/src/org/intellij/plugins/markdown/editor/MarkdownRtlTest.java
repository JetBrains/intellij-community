package org.intellij.plugins.markdown.editor;

import com.intellij.openapi.editor.impl.AbstractRtlTest;

public class MarkdownRtlTest extends AbstractRtlTest {
  private static final String SAMPLE_TEXT = """
    RRRR*R* __RRR__ RR,RR RR-RR RR (RRRR) RR 'RRR' "RR" <RRRRR> [RR]|

    |RRR:|

    * |RRR!|
    * |~RRR~|
    |RRRRRR""";

  public void testBasicCase() {
    checkBidiRunBoundaries(SAMPLE_TEXT, "md");
  }
}
