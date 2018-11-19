// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.psi.PropertiesResourceBundleUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 */
public class ResourceBundleUtilTest {

  /**
   * Holds pairs like {@code ('property value'; 'value editor text')}.
   */
  private static final String[][] TEST_DATA = {
    // Common.
    { "", "" },
    { "as-is", "as-is" },
    { "with escaped escape symbol - \\\\", "with escaped escape symbol - \\" },
    
    // Special symbols.
    {"special symbols - # and ! and = and :", "special symbols - # and ! and = and :"},
    
    // White spaces.
    { "trailing white space ", "trailing white space " },
    { "trailing white spaces   ", "trailing white spaces   " },
    { "trailing tab\t", "trailing tab\t" },
    { "trailing tabs\t\t\t", "trailing tabs\t\t\t" },
    { "\\\tstarting from tab", "\tstarting from tab" },
    { "\\\t\t\tstarting from tabs", "\t\t\tstarting from tabs" },
    { "\\ starting from white space", " starting from white space" },
    { "\\   starting from white spaces", "   starting from white spaces" },
    { "\\ \t  starting from white spaces and tabs", " \t  starting from white spaces and tabs" },
    { "first line \\\nsecond line", "first line \nsecond line" },
    { "first line \\\r\\\nsecond line", "first line \r\nsecond line" },

    // Non-ascii symbols and escaped characters
    { "wei\u00DF", "wei\u00DF" },
    { "wei\\u00DF", "wei\\u00DF" },

    // All together.
    {"\\\t text with \\\nspecial symbols:\\\n#", "\t text with \nspecial symbols:\n#"}
  };
  
  @Test
  public void checkAllTestData() {
    for (String[] entry : TEST_DATA) {
      assertEquals(
        "Expected property value differs from the one converted from value editor text",
        entry[0],
        PropertiesResourceBundleUtil.fromValueEditorToPropertyValue(entry[1], '=')
      );
      assertEquals(
        "Expected value editor text differs from the one converted from property value",
        entry[1],
        PropertiesResourceBundleUtil.fromPropertyValueToValueEditor(entry[0])
      );
    }
  }
}
