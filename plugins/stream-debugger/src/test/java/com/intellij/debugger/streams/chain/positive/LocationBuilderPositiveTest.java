package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class LocationBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testAnonymousBody() throws Exception {
    doTest();
  }

  public void testAssignExpression() throws Exception {
    doTest();
  }

  public void testFirstParameterOfFunction() throws Exception {
    doTest();
  }

  public void testLambdaBody() throws Exception {
    doTest();
  }

  public void testParameterInAssignExpression() throws Exception {
    doTest();
  }

  public void testParameterInReturnExpression() throws Exception {
    doTest();
  }

  public void testReturnExpression() throws Exception {
    doTest();
  }

  public void testSecondParameterOfFunction() throws Exception {
    doTest();
  }

  public void testSingleExpression() throws Exception {
    doTest();
  }

  @Override
  protected void checkResultChain(StreamChain chain) {
    assertNotNull(chain);
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "location";
  }
}
