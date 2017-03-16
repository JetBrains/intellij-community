package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.impl.StreamChainBuilder;
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
    final StreamChain chain = StreamChainBuilder.tryBuildChain(elementAtCaret);
    assertNotNull(chain);
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
  protected void checkResultChain(StreamChain chain) {
    throw new AssertionError();
  }

  @Override
  void doTest() throws Exception {
    throw new AssertionError();
  }
}
