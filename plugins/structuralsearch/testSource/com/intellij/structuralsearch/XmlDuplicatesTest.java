package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlDuplicatesTest extends DuplicatesTestCase {
  @NonNls
  private static final String BASE_PATH = "/xml/duplicates/";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected Language[] getLanguages() {
    return new Language[]{HTMLLanguage.INSTANCE, XMLLanguage.INSTANCE};
  }


  public void testXml1() throws Exception {
    doTest("xmldups1.xml", true, true, true, 1, -1, "_0", 2);
    doTest("xmldups1.xml", true, true, false, 1, -1, "_1", 2);
  }

  public void testXml2() throws Exception {
    doTest("xmldups2.xml", true, true, true, 1, -1, "", 2);
  }

  public void testHtml1() throws Exception {
    doTest("htmldups1.html", true, true, false, 1, -1, "_1", 5);
    doTest("htmldups1.html", true, true, true, 1, -1, "_0", 5);
  }

  public void testHtml2() throws Exception {
    doTest("htmldups2.html", true, true, true, 1, -1, "", 5);
  }

}
