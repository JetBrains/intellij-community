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

import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class StreamCallImpl implements StreamCall {
  private final String myName;
  private final List<CallArgument> myArgs;
  private final StreamCallType myType;
  private final TextRange myTextRange;

  StreamCallImpl(@NotNull String name,
                 @NotNull List<CallArgument> args,
                 @NotNull StreamCallType type,
                 @NotNull TextRange range) {
    myName = name;
    myArgs = args;
    myType = type;
    myTextRange = range;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myTextRange;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public List<CallArgument> getArguments() {
    return myArgs;
  }

  @NotNull
  @Override
  public StreamCallType getType() {
    return myType;
  }
}
