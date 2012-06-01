package com.intellij.psi.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;

/**
 * @author peter
 */
public abstract class XmlFormatterTestCase extends FormatterTestCase{

  public void testDontKeepLineBreaksInText() throws Throwable {
    final CodeStyleSettings settings = getSettings();
    final XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    settings.RIGHT_MARGIN = 15;

    settings.HTML_KEEP_LINE_BREAKS_IN_TEXT = false;
    xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT = false;
    doTextTest("<tag>aaa\nbbb\nccc\nddd\n</tag>", "<tag>aaa bbb\n    ccc ddd\n</tag>");

    settings.HTML_TEXT_WRAP = CodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_TEXT_WRAP = CodeStyleSettings.DO_NOT_WRAP;
    doTextTest("<tag>aaa\nbbb\nccc\nddd\n</tag>", "<tag>aaa bbb ccc ddd\n</tag>");
  }

}
