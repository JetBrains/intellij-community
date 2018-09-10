// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest;

import com.intellij.testFramework.ParsingTestCase;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.rest.parsing.RestParserDefinition;

/**
 * User : catherine
 */
public class RestParsingTest extends ParsingTestCase {

  public RestParsingTest() {
    super("", "rst", new RestParserDefinition());
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

  public void testDirectiveWithNewLine() {
    doTest(true);
  }

  @Override
  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/python-rest/testData/psi";
  }


  @Override
  protected boolean checkAllPsiRoots() {
    return false;
  }
}
