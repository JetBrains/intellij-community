// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class TerminationCallTypeTest {
  private final TestFixture<StreamChainBuilderFixture> builderFixture = streamChainBuilderFixture("chain/positive/terminationType");

  @Test
  public void testVoidType() {
    doTest(JavaTypes.INSTANCE.getVOID());
  }

  @Test
  public void testBooleanType() {
    doTest(JavaTypes.INSTANCE.getBOOLEAN());
  }

  @Test
  public void testIntType() {
    doTest(JavaTypes.INSTANCE.getINT());
  }

  @Test
  public void testDoubleType() {
    doTest(JavaTypes.INSTANCE.getDOUBLE());
  }

  @Test
  public void testLongType() {
    doTest(JavaTypes.INSTANCE.getLONG());
  }

  @Test
  public void testReferenceType() {
    doTest(JavaTypes.INSTANCE.array(JavaTypes.INSTANCE.getINT()));
  }

  private void doTest(@NotNull GenericType returnType) {
    final StreamChainBuilderFixture fixture = builderFixture.get();
    final PsiElement elementAtCaret = fixture.configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    final List<StreamChain> chains = ReadAction.computeBlocking(() -> fixture.getChainBuilder().build(elementAtCaret));
    assertEquals(1, chains.size());

    final StreamChain chain = chains.getFirst();
    assertNotNull(chain);
    assertEquals(returnType, chain.getTerminationCall().getResultType());
  }
}
