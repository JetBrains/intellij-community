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
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.ui.ChooserOption;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChainOption implements ChooserOption {
  private final StreamChain myChain;

  public StreamChainOption(@NotNull StreamChain chain) {
    myChain = chain;
  }

  @NotNull
  @Override
  public Stream<TextRange> rangeStream() {
    return Stream.of(
      new TextRange(myChain.getProducerCall().getTextRange().getStartOffset(), myChain.getTerminationCall().getTextRange().getEndOffset()));
  }

  @NotNull
  @Override
  public String getText() {
    return myChain.getText();
  }

  @NotNull
  public StreamChain getChain() {
    return myChain;
  }
}
