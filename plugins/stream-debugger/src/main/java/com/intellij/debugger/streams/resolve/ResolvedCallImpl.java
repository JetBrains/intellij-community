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
package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.wrapper.MethodCall;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class ResolvedCallImpl implements ResolvedCall {
  private final MethodCall myMethodCall;
  private final Map<Value, List<Value>> myPrevious;
  private final Map<Value, List<Value>> myNext;

  public ResolvedCallImpl(@NotNull MethodCall call,
                          @NotNull Map<Value, List<Value>> input,
                          @NotNull Map<Value, List<Value>> output) {
    myMethodCall = call;
    myPrevious = input;
    myNext = output;
  }

  @NotNull
  @Override
  public String getName() {
    return myMethodCall.getName();
  }

  @NotNull
  @Override
  public String getArguments() {
    return myMethodCall.getArguments();
  }

  @NotNull
  @Override
  public List<Value> getPreviousValues(@NotNull Value value) {
    return extractList(myPrevious, value);
  }

  @NotNull
  @Override
  public List<Value> getNextValues(@NotNull Value value) {
    return extractList(myNext, value);
  }

  @NotNull
  @Override
  public List<Value> getValues() {
    return Collections.unmodifiableList(new ArrayList<>(myNext.keySet()));
  }

  @NotNull
  private static List<Value> extractList(@NotNull Map<Value, List<Value>> values, @NotNull Value key) {
    final List<Value> result = values.get(key);
    return result == null ? Collections.emptyList() : Collections.unmodifiableList(result);
  }
}
