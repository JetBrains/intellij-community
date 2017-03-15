package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.impl.StreamChainBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.debugger.streams.trace.smart.handler.type.GenericType.INT;
import static com.intellij.debugger.streams.trace.smart.handler.type.GenericType.OBJECT;

/**
 * @author Vitaliy.Bibaev
 */
public class PrimitiveObjectBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testSimple() {
    doTest(OBJECT, OBJECT);
  }

  private void doTest(@NotNull GenericType producerAfterType,
                      @NotNull GenericType terminatorBeforeType,
                      @NotNull GenericType... intermediateAfterTypes) {
    final PsiElement elementAtCaret = configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    final StreamChain chain = StreamChainBuilder.tryBuildChain(elementAtCaret);
    assertNotNull(chain);
    final List<IntermediateStreamCall> intermediateCalls = chain.getIntermediateCalls();
    assertEquals(intermediateAfterTypes.length, intermediateCalls.size());
    assertEquals(producerAfterType, chain.getProducerCall().getTypeAfter());
    assertEquals(terminatorBeforeType, chain.getTerminationCall().getTypeBefore());

    if (intermediateAfterTypes.length > 0) {
      assertEquals(producerAfterType, intermediateCalls.get(0).getTypeBefore());
      for (int i = 0; i < intermediateAfterTypes.length - 1; i++) {
        assertEquals(intermediateAfterTypes[i], intermediateCalls.get(i).getTypeAfter());
        assertEquals(intermediateAfterTypes[i], intermediateCalls.get(i + 1).getTypeBefore());
      }

      final GenericType lastAfterType = intermediateAfterTypes[intermediateAfterTypes.length - 1];
      assertEquals(lastAfterType, chain.getTerminationCall().getTypeBefore());
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
