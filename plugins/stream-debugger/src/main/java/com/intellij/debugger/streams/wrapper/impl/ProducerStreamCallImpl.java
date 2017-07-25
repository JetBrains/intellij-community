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
package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.ProducerStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerStreamCallImpl extends StreamCallImpl implements ProducerStreamCall {
  private final GenericType myTypeAfter;

  public ProducerStreamCallImpl(@NotNull String name,
                                @NotNull List<CallArgument> args,
                                @NotNull GenericType typeAfter,
                                @NotNull TextRange range,
                                @NotNull String packageName) {
    super(name, args, StreamCallType.PRODUCER, range, packageName);
    myTypeAfter = typeAfter;
  }

  @NotNull
  @Override
  public GenericType getTypeAfter() {
    return myTypeAfter;
  }
}
