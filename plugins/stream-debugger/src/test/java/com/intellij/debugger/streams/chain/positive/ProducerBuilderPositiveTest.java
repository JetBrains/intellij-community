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

import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerBuilderPositiveTest extends StreamChainBuilderPositiveTestBase {
  public void testCollectionStream() throws Exception {
    doTest();
  }

  public void testCustomSource() throws Exception {
    doTest();
  }

  public void testIntStreamRange() throws Exception {
    doTest();
  }

  public void testIntStreamRangeClosed() throws Exception {
    doTest();
  }

  public void testIterate() throws Exception {
    doTest();
  }

  public void testConcat() throws Exception {
    doTest();
  }

  @Override
  protected void checkResultChains(@NotNull List<StreamChain> chains) {
    assertFalse(chains.isEmpty());
    assertNotNull(chains.get(0).getProducerCall());
  }

  @NotNull
  @Override
  protected String getDirectoryName() {
    return "producer";
  }
}
