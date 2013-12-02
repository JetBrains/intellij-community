package org.jetbrains.postfixCompletion.templates;

import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/templates/if")
public class ElseStatementPostfixTemplateTest extends PostfixTemplateTestCase {

  public void testBooleanVariable() throws Exception {
    doTest();
  }
  
  public void testBitOperations() throws Exception {
    doTest();
  }

  public void testBitOperationsWithMethod() throws Exception {
    doTest();
  }
  
  public void testUnresolvedVariable() throws Exception {
    doTest();
  }

  public void testInstanceof() throws Exception {
    doTest();
  }

  public void testIntegerComparison() throws Exception {
    doTest();
  }

  public void testLogicalOperations() throws Exception {
    doTest();
  }

  public void testNotNull() throws Exception {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return "testData/templates/else";
  }
}
