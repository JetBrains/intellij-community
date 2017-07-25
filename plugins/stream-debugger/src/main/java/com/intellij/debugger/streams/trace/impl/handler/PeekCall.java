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
package com.intellij.debugger.streams.trace.impl.handler;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.CallArgument;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekCall implements IntermediateStreamCall {
  private final List<CallArgument> myLambda;
  private final GenericType myElementType;

  public PeekCall(@NotNull String lambda, @NotNull GenericType elementType) {
    myLambda = Collections.singletonList(new CallArgumentImpl(CommonClassNames.JAVA_LANG_OBJECT, lambda));
    myElementType = elementType;
  }

  @NotNull
  @Override
  public String getName() {
    return "peek";
  }

  @NotNull
  @Override
  public String getPackageName() {
    return "java.util.stream";
  }

  @NotNull
  @Override
  public List<CallArgument> getArguments() {
    return myLambda;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @NotNull
  @Override
  public StreamCallType getType() {
    return StreamCallType.INTERMEDIATE;
  }

  @NotNull
  @Override
  public GenericType getTypeAfter() {
    return myElementType;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myElementType;
  }
}
