package com.intellij.structuralsearch;

import com.intellij.dupLocator.DefaultDuplocatorState;
import com.intellij.dupLocator.DuplicatesTestCase;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;

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


  @Override
  protected void findAndCheck(String fileName,
                              boolean distinguishVars,
                              boolean distinguishMethods,
                              boolean distinguishListerals,
                              int patternCount,
                              String suffix,
                              int lowerBound) throws Exception {
    final DefaultDuplocatorState xmlState = (DefaultDuplocatorState)DuplocatorUtil.registerAndGetState(XMLLanguage.INSTANCE);
    final DefaultDuplocatorState htmlState = (DefaultDuplocatorState)DuplocatorUtil.registerAndGetState(HTMLLanguage.INSTANCE);

    final boolean xmlOldLits = xmlState.DISTINGUISH_LITERALS;
    final int xmlOldLowerBound = xmlState.LOWER_BOUND;

    final boolean htmlOldLits = htmlState.DISTINGUISH_LITERALS;
    final int htmlOldLowerBound = htmlState.LOWER_BOUND;

    try {
      if ("html".equals(FileUtil.getExtension(fileName))) {
        htmlState.DISTINGUISH_LITERALS = distinguishListerals;
        htmlState.LOWER_BOUND = lowerBound;
      }
      else {
        xmlState.DISTINGUISH_LITERALS = distinguishListerals;
        xmlState.LOWER_BOUND = lowerBound;
      }

      doFindAndCheck(fileName, patternCount, suffix);
    }
    finally {
      xmlState.DISTINGUISH_LITERALS = xmlOldLits;
      xmlState.LOWER_BOUND = xmlOldLowerBound;
      htmlState.DISTINGUISH_LITERALS = htmlOldLits;
      htmlState.LOWER_BOUND = htmlOldLowerBound;
    }
  }
}
                                                                    