// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.testFramework.ParsingTestCase;
import com.jetbrains.python.PythonHelpersLocator;
import com.intellij.python.reStructuredText.parsing.RestParserDefinition;

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
