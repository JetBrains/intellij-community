// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.chain.positive;

import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminationCallTypeTest extends StreamChainBuilderPositiveTestBase {
  public void testVoidType() {
    doTest(JavaTypes.INSTANCE.getVOID());
  }

  public void testBooleanType() {
    doTest(JavaTypes.INSTANCE.getBOOLEAN());
  }

  public void testIntType() {
    doTest(JavaTypes.INSTANCE.getINT());
  }

  public void testDoubleType() {
    doTest(JavaTypes.INSTANCE.getDOUBLE());
  }

  public void testLongType() {
    doTest(JavaTypes.INSTANCE.getLONG());
  }

  public void testReferenceType() {
    doTest(JavaTypes.INSTANCE.array(JavaTypes.INSTANCE.getINT()));
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "terminationType";
  }

  protected void doTest(@NotNull GenericType returnType) {
    final PsiElement elementAtCaret = configureAndGetElementAtCaret();
    assertNotNull(elementAtCaret);
    final List<StreamChain> chains = getChainBuilder().build(elementAtCaret);
    assertEquals(1, chains.size());

    final StreamChain chain = chains.get(0);
    assertNotNull(chain);
    assertEquals(returnType, chain.getTerminationCall().getResultType());
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
  }
}
