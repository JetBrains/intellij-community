// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.psi.PropertiesResourceBundleUtil;
import com.intellij.lang.properties.psi.PropertyKeyValueFormat;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

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
  public void checkConvertToPresentableFormat() {
    for (String[] entry : TEST_DATA) {
      assertEquals(
        "Expected property value differs from the one converted from value editor text",
        entry[0],
        PropertiesResourceBundleUtil.convertValueToFileFormat(entry[1], '=', PropertyKeyValueFormat.PRESENTABLE)
      );
      assertEquals(
        "Expected value editor text differs from the one converted from property value",
        entry[1],
        PropertiesResourceBundleUtil.fromPropertyValueToValueEditor(entry[0])
      );
    }
  }


  private static final String[][] MEMORY_FORMAT_TEST_DATA = {
    {"", ""},
    {"abc", "abc"},
    {"a\nb", "a\\nb"},
    {"a b c ", "a b c "},
    {" a b c ", "\\ a b c "},
    {" a\naa  cc", "\\ a\\naa  cc"},
    {"abc\r\n", "abc\\r\\n"}
  };

  @Test
  public void checkConvertFromMemoryFormat() throws IOException {
    for (String[] entry : MEMORY_FORMAT_TEST_DATA) {
      String valueInMemory = entry[0];
      String valueInFile = entry[1];
      Properties properties = new Properties();
      properties.load(new StringReader("key=" + valueInFile));
      String value = properties.getProperty("key");
      assertEquals(valueInMemory, value);
      String converted = PropertiesResourceBundleUtil.convertValueToFileFormat(valueInMemory, '=', PropertyKeyValueFormat.MEMORY);
      assertEquals("Expected in-memory value differs from the one converted from property value",
                   valueInFile, converted);
    }
  }
}
