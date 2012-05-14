package com.wrq.rearranger.settings;

/**
 * Test fill string pattern generation.
 */

import junit.framework.TestCase;

public class CommentFillStringTest
  extends TestCase
{
  CommentFillString cfs;

  public void testGetFillStringPattern() throws Exception {
    String result;
    cfs = new CommentFillString();
    cfs.setFillString("a");
    result = cfs.getFillStringPattern();
    assertEquals("(a)*", result);
    cfs.setFillString("abc");
    result = cfs.getFillStringPattern();
    assertEquals("(abc)*(ab|a)?", result);
    cfs.setFillString("-=*");
    result = cfs.getFillStringPattern();
    assertEquals("(-=\\*)*(-=|-)?", result);
  }
}