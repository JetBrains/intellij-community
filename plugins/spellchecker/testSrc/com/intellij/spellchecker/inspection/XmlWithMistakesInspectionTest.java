package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class XmlWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {

  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/xmlWithMistakes";
  }

  
  public void testXml() throws Throwable {
    doTest("test.xml", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testJsp() throws Throwable {
    doTest("test.jsp", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

}


