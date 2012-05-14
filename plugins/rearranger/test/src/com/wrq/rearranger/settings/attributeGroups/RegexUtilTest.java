package com.wrq.rearranger.settings.attributeGroups;

/**
 * Todo - describe class.
 * <p/>
 * Corresponds to portions of: todo - insert .c and .h file references
 */

import junit.framework.TestCase;

public class RegexUtilTest
  extends TestCase
{
  RegexUtil regexUtil;

  public void testReplaceAllFS() throws Exception {
    String result;
    result = RegexUtil.replaceAllFS("abc", "");
    assertEquals("abc", result);
    result = RegexUtil.replaceAllFS("ab%FS%c", "");
    assertEquals("abc", result);
    result = RegexUtil.replaceAllFS("%FS%ab%FS%", "xx");
    assertEquals("xxabxx", result);
  }
}