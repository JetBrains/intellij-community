package com.intellij.util.xml;

import com.intellij.openapi.util.text.DelimitedListProcessor;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class DelimitedListProcessorTest extends TestCase {

  public void testProcessor() {
    doTest("a; ; ", Arrays.asList("a", " ", " "));
  }

  private void doTest(final String text, final List<String> expected) {
    final ArrayList<String> tokens = new ArrayList<String>();
    new DelimitedListProcessor(";") {
      @Override
      protected void processToken(final int start, final int end, final boolean delimitersOnly) {
        tokens.add(text.substring(start, end));
      }
    }.processText(text);
    assertEquals(expected, tokens);
  }
}
