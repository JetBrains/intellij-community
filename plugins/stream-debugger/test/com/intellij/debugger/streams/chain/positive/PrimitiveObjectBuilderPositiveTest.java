// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.test.StreamChainBuilderFixture;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.debugger.streams.test.StreamChainBuilderFixtureKt.streamChainBuilderFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class PrimitiveObjectBuilderPositiveTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/primitive");

  @Test
  public void testSimpleObject() {
    doTest(JavaTypes.INSTANCE.getANY());
  }

  @Test
  public void testSimpleInt() {
    doTest(JavaTypes.INSTANCE.getINT());
  }

  @Test
  public void testSimpleDouble() {
    doTest(JavaTypes.INSTANCE.getDOUBLE());
  }

  @Test
  public void testSimpleLong() {
    doTest(JavaTypes.INSTANCE.getLONG());
  }

  @Test
  public void testObj2Int() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getINT());
  }

  @Test
  public void testObj2Long() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getLONG());
  }

  @Test
  public void testObj2Double() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getDOUBLE());
  }

  @Test
  public void testPrimitiveIdentity() {
    doTest(JavaTypes.INSTANCE.getINT(), JavaTypes.INSTANCE.getINT());
  }

  @Test
  public void testPrimitive2Obj() {
    doTest(JavaTypes.INSTANCE.getDOUBLE(), JavaTypes.INSTANCE.getANY());
  }

  @Test
  public void testFewTransitions() {
    doTest(JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getINT(), JavaTypes.INSTANCE.getINT(),
           JavaTypes.INSTANCE.getANY(), JavaTypes.INSTANCE.getDOUBLE(), JavaTypes.INSTANCE.getANY(),
           JavaTypes.INSTANCE.getLONG());
  }

  private void doTest(@NotNull GenericType producerAfterType,
                      GenericType @NotNull ... intermediateAfterTypes) {
    final StreamChainBuilderFixture fixture = builderFixture.get();
    final PsiElement elementAtCaret = fixture.configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    final List<StreamChain> chains = ReadAction.computeBlocking(() -> fixture.getChainBuilder().build(elementAtCaret));
    assertFalse(chains.isEmpty());
    final StreamChain chain = chains.getFirst();
    final List<IntermediateStreamCall> intermediateCalls = chain.getIntermediateCalls();
    assertEquals(intermediateAfterTypes.length, intermediateCalls.size());
    assertEquals(producerAfterType, chain.getQualifierExpression().getTypeAfter());

    if (intermediateAfterTypes.length > 0) {
      assertEquals(producerAfterType, intermediateCalls.getFirst().getTypeBefore());
      for (int i = 0; i < intermediateAfterTypes.length - 1; i++) {
        assertEquals(intermediateAfterTypes[i], intermediateCalls.get(i).getTypeAfter());
        assertEquals(intermediateAfterTypes[i], intermediateCalls.get(i + 1).getTypeBefore());
      }

      final GenericType lastAfterType = intermediateAfterTypes[intermediateAfterTypes.length - 1];
      assertEquals(lastAfterType, chain.getTerminationCall().getTypeBefore());
      final IntermediateStreamCall lastCall = intermediateCalls.getLast();
      assertEquals(lastAfterType, lastCall.getTypeAfter());
    }
    else {
      assertEquals(producerAfterType, chain.getTerminationCall().getTypeBefore());
    }
  }
}
