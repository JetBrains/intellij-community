package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.common.XmlWithMistakesInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class XmlWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  public String getDataPath() {
    return "/inspection/xmlWithMistakes/data";
  }

  public void testXml() throws Exception {
    doTest(getTestName(true), new XmlWithMistakesInspection());
  }

  

}