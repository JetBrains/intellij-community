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
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminationCallTypeTest extends StreamChainBuilderPositiveTestBase {
  public void testVoidType() throws Exception {
    doTest(JavaTypes.INSTANCE.getVoidType());
  }

  public void testBooleanType() throws Exception {
    doTest(JavaTypes.INSTANCE.getBooleanType());
  }

  public void testIntType() throws Exception {
    doTest(JavaTypes.INSTANCE.getIntegerType());
  }

  public void testDoubleType() throws Exception {
    doTest(JavaTypes.INSTANCE.getDoubleType());
  }

  public void testLongType() throws Exception {
    doTest(JavaTypes.INSTANCE.getLongType());
  }

  public void testReferenceType() throws Exception {
    doTest(JavaTypes.INSTANCE.array(JavaTypes.INSTANCE.getIntegerType()));
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "terminationType";
  }

  protected void doTest(@NotNull GenericType returnType) throws Exception {
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
