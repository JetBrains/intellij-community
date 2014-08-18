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
package com.intellij.psi.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;

/**
 * @author peter
 */
public abstract class XmlFormatterTestCase extends FormatterTestCase{

  public void testDontKeepLineBreaksInText() throws Throwable {
    final CodeStyleSettings settings = getSettings();
    final XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    settings.setDefaultRightMargin(15);

    settings.HTML_KEEP_LINE_BREAKS_IN_TEXT = false;
    xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT = false;
    doTextTest("<tag>aaa\nbbb\nccc\nddd\n</tag>", "<tag>aaa bbb\n    ccc ddd\n</tag>");

    settings.HTML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest("<tag>aaa\nbbb\nccc\nddd\n</tag>", "<tag>aaa bbb ccc ddd\n</tag>");
  }

}
