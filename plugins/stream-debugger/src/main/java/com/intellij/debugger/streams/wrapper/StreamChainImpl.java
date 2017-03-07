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
package com.intellij.debugger.streams.wrapper;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChainImpl implements StreamChain {
  private final StreamCall myProducer;
  private final List<StreamCall> myIntermediateCalls;
  private final StreamCall myTerminator;

  public StreamChainImpl(@NotNull StreamCall producer, @NotNull List<StreamCall> intermediateCalls, @NotNull StreamCall terminator) {
    myProducer = producer;
    myIntermediateCalls = intermediateCalls;
    myTerminator = terminator;
  }

  @NotNull
  @Override
  public StreamCall getProducerCall() {
    return myProducer;
  }

  @NotNull
  @Override
  public List<StreamCall> getIntermediateCalls() {
    return Collections.unmodifiableList(myIntermediateCalls);
  }

  @NotNull
  @Override
  public StreamCall getCall(int index) {
    if (0 <= index && index < length()) {
      return doGetCall(index);
    }

    throw new IndexOutOfBoundsException("Call index out of bound: " + index);
  }

  @NotNull
  @Override
  public StreamCall getTerminationCall() {
    return myTerminator;
  }

  @NotNull
  @Override
  public String getText() {
    final Iterator<StreamCall> iterator = StreamEx.of(myProducer).append(myIntermediateCalls).append(myTerminator).iterator();
    final StringBuilder builder = new StringBuilder();

    while (iterator.hasNext()) {
      final MethodCall call = iterator.next();
      builder.append(call.getName()).append(call.getArguments());
      if (iterator.hasNext()) {
        builder.append(EvaluateExpressionTracerBase.LINE_SEPARATOR).append(".");
      }
    }

    return builder.toString();
  }

  @Override
  public int length() {
    return 2 + myIntermediateCalls.size();
  }

  private StreamCall doGetCall(int index) {
    if (index == 0) {
      return myProducer;
    }

    if (index <= myIntermediateCalls.size()) {
      return myIntermediateCalls.get(index - 1);
    }

    return myTerminator;
  }
}
