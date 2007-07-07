/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 22, 2006
 */
public class StringUtilTest extends TestCase {
  public void testToUpperCase() {
    assertEquals('/', StringUtil.toUpperCase('/'));
    assertEquals(':', StringUtil.toUpperCase(':'));
    assertEquals('A', StringUtil.toUpperCase('a'));
    assertEquals('A', StringUtil.toUpperCase('A'));
    assertEquals('K', StringUtil.toUpperCase('k'));
    assertEquals('K', StringUtil.toUpperCase('K'));
    
    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }
  
  public void testToLowerCase() {
    assertEquals('/', StringUtil.toLowerCase('/'));
    assertEquals(':', StringUtil.toLowerCase(':'));
    assertEquals('a', StringUtil.toLowerCase('a'));
    assertEquals('a', StringUtil.toLowerCase('A'));
    assertEquals('k', StringUtil.toLowerCase('k'));
    assertEquals('k', StringUtil.toLowerCase('K'));

    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }

  public void testSplitWithQuotes() {
    final List<String> strings = StringUtil.splitHonorQuotes("aaa bbb   ccc \"ddd\" \"e\\\"e\\\"e\"  ", ' ');
    assertEquals(5, strings.size());
    assertEquals("aaa", strings.get(0));
    assertEquals("bbb", strings.get(1));
    assertEquals("ccc", strings.get(2));
    assertEquals("\"ddd\"", strings.get(3));
    assertEquals("\"e\\\"e\\\"e\"", strings.get(4));
  }

  public void testUnpluralize() {
    assertEquals("s", StringUtil.unpluralize("s"));
    assertEquals("z", StringUtil.unpluralize("zs"));
  }
}
