package com.intellij.structuralsearch;

import com.intellij.dupLocator.DuplicatesTestCase;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.PathManager;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlDuplicatesTest extends DuplicatesTestCase {

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/structuralsearch/testData/xml/duplicates/";
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
