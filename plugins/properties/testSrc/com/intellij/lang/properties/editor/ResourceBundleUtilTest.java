/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.psi.PropertiesResourceBundleUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 10/5/11 2:37 PM
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
