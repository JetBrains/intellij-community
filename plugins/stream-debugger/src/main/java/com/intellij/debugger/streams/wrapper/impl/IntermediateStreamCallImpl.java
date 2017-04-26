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
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateStreamCallImpl extends StreamCallImpl implements IntermediateStreamCall {

  private final GenericType myTypeBefore;
  private final GenericType myTypeAfter;

  public IntermediateStreamCallImpl(@NotNull String name,
                                    @NotNull String args,
                                    @NotNull GenericType typeBefore,
                                    @NotNull GenericType typeAfter,
                                    @NotNull TextRange range) {
    super(name, args, StreamCallType.INTERMEDIATE, range);
    myTypeBefore = typeBefore;
    myTypeAfter = typeAfter;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myTypeBefore;
  }

  @NotNull
  @Override
  public GenericType getTypeAfter() {
    return myTypeAfter;
  }
}
