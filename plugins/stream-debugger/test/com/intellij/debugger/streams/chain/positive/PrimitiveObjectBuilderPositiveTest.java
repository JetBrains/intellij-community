// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveObjectBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testSimpleObject() {
    doTest(JavaTypes.INSTANCE.getANY());
  }

  public void testSimpleInt() {
    doTest(JavaTypes.INSTANCE.getINT());
  }

  public void testSimpleDouble() {
    doTest(JavaTypes.INSTANCE.getDOUBLE());
  }

  public void testSimpleLong() {
    doTest(JavaTypes.INSTANCE.getLONG());
  }

  public void testObj2Int() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getINT());
  }

  public void testObj2Long() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getLONG());
  }

  public void testObj2Double() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getDOUBLE());
  }

  public void testPrimitiveIdentity() {
    doTest(JavaTypes.INSTANCE.getINT(), JavaTypes.INSTANCE.getINT());
  }

  public void testPrimitive2Obj() {
    doTest(JavaTypes.INSTANCE.getDOUBLE(), JavaTypes.INSTANCE.getANY());
  }

  public void testFewTransitions() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getINT(), JavaTypes.INSTANCE.getINT(),
           JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getDOUBLE(), JavaTypes.INSTANCE.getANY(),
           JavaTypes.INSTANCE.getLONG());
  }

  private void doTest(@NotNull GenericType producerAfterType,
                      @NotNull GenericType... intermediateAfterTypes) {
    final PsiElement elementAtCaret = configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    final List<StreamChain> chains = getChainBuilder().build(elementAtCaret);
    assertFalse(chains.isEmpty());
    final StreamChain chain = chains.get(0);
    final List<IntermediateStreamCall> intermediateCalls = chain.getIntermediateCalls();
    assertEquals(intermediateAfterTypes.length, intermediateCalls.size());
    assertEquals(producerAfterType, chain.getQualifierExpression().getTypeAfter());

    if (intermediateAfterTypes.length > 0) {
      assertEquals(producerAfterType, intermediateCalls.get(0).getTypeBefore());
      for (int i = 0; i < intermediateAfterTypes.length - 1; i++) {
        assertEquals(intermediateAfterTypes[i], intermediateCalls.get(i).getTypeAfter());
        assertEquals(intermediateAfterTypes[i], intermediateCalls.get(i + 1).getTypeBefore());
      }

      final GenericType lastAfterType = intermediateAfterTypes[intermediateAfterTypes.length - 1];
      assertEquals(lastAfterType, chain.getTerminationCall().getTypeBefore());
      final IntermediateStreamCall lastCall = intermediateCalls.get(intermediateCalls.size() - 1);
      assertEquals(lastAfterType, lastCall.getTypeAfter());
    }
    else {
      assertEquals(producerAfterType, chain.getTerminationCall().getTypeBefore());
    }
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "primitive";
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    throw new AssertionError();

  }

  @Override
  void doTest() {
    throw new AssertionError();
  }
}
