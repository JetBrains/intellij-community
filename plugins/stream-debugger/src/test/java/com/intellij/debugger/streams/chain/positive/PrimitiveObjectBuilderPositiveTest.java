/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.debugger.streams.trace.impl.handler.type.GenericType.*;

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveObjectBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testSimpleObject() {
    doTest(JavaTypes.INSTANCE.getAnyType());
  }

  public void testSimpleInt() {
    doTest(JavaTypes.INSTANCE.getIntegerType());
  }

  public void testSimpleDouble() {
    doTest(JavaTypes.INSTANCE.getDoubleType());
  }

  public void testSimpleLong() {
    doTest(JavaTypes.INSTANCE.getLongType());
  }

  public void testObj2Int() {
    doTest(JavaTypes.INSTANCE.getAnyType(), JavaTypes.INSTANCE.getIntegerType());
  }

  public void testObj2Long() {
    doTest(JavaTypes.INSTANCE.getAnyType(), JavaTypes.INSTANCE.getLongType());
  }

  public void testObj2Double() {
    doTest(JavaTypes.INSTANCE.getAnyType(), JavaTypes.INSTANCE.getDoubleType());
  }

  public void testPrimitiveIdentity() {
    doTest(JavaTypes.INSTANCE.getIntegerType(), JavaTypes.INSTANCE.getIntegerType());
  }

  public void testPrimitive2Obj() {
    doTest(JavaTypes.INSTANCE.getDoubleType(), JavaTypes.INSTANCE.getAnyType());
  }

  public void testFewTransitions() {
    doTest(JavaTypes.INSTANCE.getAnyType(), JavaTypes.INSTANCE.getIntegerType(), JavaTypes.INSTANCE.getIntegerType(),
           JavaTypes.INSTANCE.getAnyType(), JavaTypes.INSTANCE.getDoubleType(), JavaTypes.INSTANCE.getAnyType(),
           JavaTypes.INSTANCE.getLongType());
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
