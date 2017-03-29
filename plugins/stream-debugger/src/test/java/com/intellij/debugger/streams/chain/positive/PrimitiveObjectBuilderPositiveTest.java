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
    doTest(OBJECT);
  }

  public void testSimpleInt() {
    doTest(INT);
  }

  public void testSimpleDouble() {
    doTest(DOUBLE);
  }

  public void testSimpleLong() {
    doTest(LONG);
  }

  public void testObj2Int() {
    doTest(OBJECT, INT);
  }

  public void testObj2Long() {
    doTest(OBJECT, LONG);
  }

  public void testObj2Double() {
    doTest(OBJECT, DOUBLE);
  }

  public void testPrimitiveIdentity() {
    doTest(INT, INT);
  }

  public void testPrimitive2Obj() {
    doTest(DOUBLE, OBJECT);
  }

  public void testFewTransitions() {
    doTest(OBJECT, INT, INT, OBJECT, DOUBLE, OBJECT, LONG);
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
    assertEquals(producerAfterType, chain.getProducerCall().getTypeAfter());

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
  void doTest() throws Exception {
    throw new AssertionError();
  }
}
