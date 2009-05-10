package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.common.XmlWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class XmlWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return "/plugins/spellchecker/core/testData/inspection/xmlWithMistakes";
  }

  
  public void testXml() throws Throwable {
    doTest("test.xml", new XmlWithMistakesInspection());
  }

  public void testJsp() throws Throwable {
    doTest("test.jsp", new XmlWithMistakesInspection());
  }

}


