package com.wrq.rearranger.util;

/**
 * Test comment expansion with fill patterns.
 */

import junit.framework.TestCase;

public class CommentUtilTest
  extends TestCase
{
  CommentUtil commentUtil;

  public void testExpandFill() throws Exception {
    String result;
    // test left justify, right justify, center justify, multiline, multiple fills
    result = CommentUtil.expandFill("Method Name %FS%\n", 30, 4, "-=");
    assertEquals("left justify failed", "Method Name -=-=-=-=-=-=-=-=-=\n", result);
    result = CommentUtil.expandFill("%FS% Method Name\n", 30, 4, "-=");
    assertEquals("right justify failed", "-=-=-=-=-=-=-=-=-= Method Name\n", result);
    result = CommentUtil.expandFill("%FS% Method Name %FS%\n", 30, 4, "-=");
    assertEquals("center justify failed", "-=-=-=-=- Method Name -=-=-=-=\n", result);
    result = CommentUtil.expandFill("Method Name %FS%\n" +
                                    "%FS% Method Name\n" +
                                    "%FS% Method Name %FS%\n\n", 30, 4, "-=");
    assertEquals("multiline failed", "Method Name -=-=-=-=-=-=-=-=-=\n" +
                                     "-=-=-=-=-=-=-=-=-= Method Name\n" +
                                     "-=-=-=-=- Method Name -=-=-=-=\n\n", result);
    result = CommentUtil.expandFill("Method Name %FS%\n" +
                                    "%FS% Method Name\n" +
                                    "%FS% Method Name %FS%\n", 30, 4, "-=");
    assertEquals("multiline failed", "Method Name -=-=-=-=-=-=-=-=-=\n" +
                                     "-=-=-=-=-=-=-=-=-= Method Name\n" +
                                     "-=-=-=-=- Method Name -=-=-=-=\n", result);
    result = CommentUtil.expandFill("Method Name %FS%\n" +
                                    "%FS% Method Name\n" +
                                    "%FS% Method Name %FS%", 30, 4, "-=");
    assertEquals("multiline failed", "Method Name -=-=-=-=-=-=-=-=-=\n" +
                                     "-=-=-=-=-=-=-=-=-= Method Name\n" +
                                     "-=-=-=-=- Method Name -=-=-=-=", result);
    result = CommentUtil.expandFill("1%FS%2%FS%3%FS%4\n", 36, 4, "abcde");
    assertEquals("multiple fills failed", "1abcdeabcdea2abcdeabcdea3abcdeabcde4\n", result);
    // test no fill with too few and too many characters
    result = CommentUtil.expandFill("Method Name", 30, 4, "-=");
    assertEquals("no fill failed", "Method Name", result);
    result = CommentUtil.expandFill("Method Name", 5, 4, "-=");
    assertEquals("no fill failed", "Method Name", result);
    result = CommentUtil.expandFill("Method Name\n", 30, 4, "-=");
    assertEquals("no fill failed", "Method Name\n", result);
    result = CommentUtil.expandFill("Method Name\n", 5, 4, "-=");
    assertEquals("no fill failed", "Method Name\n", result);
    result = CommentUtil.expandFill("\n\t// Fields %FS%\n", 80, 4, "=");
    assertEquals("tab fill failed",
                 "\n\t// Fields ==================================================================\n",
                 result);
  }
}