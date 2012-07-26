package com.jetbrains.rest;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.ParsingTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.rest.parsing.RestParserDefinition;

/**
 * User : catherine
 */
public class RestParsingTest extends ParsingTestCase {

  public RestParsingTest() {
    super("", "rst", new RestParserDefinition());
    PyTestCase.initPlatformPrefix();
  }

  public void testTitle() {
    doTest(true);
  }

  public void testInjection() {
    doTest(true);
  }

  public void testReference() {
    doTest(true);
  }

  public void testReferenceTarget() {
    doTest(true);
  }

  public void testSubstitution() {
    doTest(true);
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/python/testData/rest/psi";
  }


  protected boolean checkAllPsiRoots() {
    return false;
  }
}
