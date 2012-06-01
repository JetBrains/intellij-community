package com.intellij.xml;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.xml.refactoring.SchemaPrefixRenameHandler;

/**
 * @author Konstantin Bulenkov
 */
public class XmlSchemaPrefixTest extends CodeInsightFixtureTestCase {

  public void testPrefixUsages() throws Exception {
    doFindUsages("usages.xml", 16);
    doFindUsages("usages1.xml", 16);
  }

  public void testRename() throws Exception {doRename();}
  public void testRename1() throws Exception {doRename();}

  public void testRename2() throws Exception {doRename();}
  public void testRenameFromClosingTag() throws Exception {doRename();}

  private void doRename() throws Exception {
    doRename("xsd");
  }

  private void doRename(String newValue) throws Exception {
    final String name = getTestName(true);
    CodeInsightTestUtil.doInlineRenameTest(new SchemaPrefixRenameHandler(), name, "xml", newValue, myFixture);
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/schemaPrefix";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  protected void doFindUsages(String filename, int usages) {
    final int size = myFixture.testFindUsages(filename).size();
    assert size == usages : "Threre should be " + usages + " usages, but found " + size + ". File: " + filename;
  }

}
