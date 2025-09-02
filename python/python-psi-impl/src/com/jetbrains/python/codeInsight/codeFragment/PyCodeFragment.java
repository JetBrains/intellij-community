/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CodeFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class PyCodeFragment extends CodeFragment {
  private final @NotNull Map<@NotNull String, @NotNull String> myInputTypes;
  private final @Nullable String myOutputType;
  private final @NotNull Set<@NotNull String> myGlobalWrites;
  private final @NotNull Set<@NotNull String> myNonlocalWrites;
  private final boolean myYieldInside;
  private final boolean myAsync;

  public PyCodeFragment(final @NotNull Set<@NotNull String> input,
                        final @NotNull Set<@NotNull String> output,
                        final @NotNull Map<@NotNull String, @NotNull String> inputTypes,
                        final @Nullable String outputType,
                        final @NotNull Set<@NotNull String> globalWrites,
                        final @NotNull Set<@NotNull String> nonlocalWrites,
                        final boolean returnInside,
                        final boolean yieldInside,
                        final boolean isAsync) {
    super(input, output, returnInside);
    myInputTypes = inputTypes;
    myOutputType = outputType;
    myGlobalWrites = globalWrites;
    myNonlocalWrites = nonlocalWrites;
    myYieldInside = yieldInside;
    myAsync = isAsync;
  }

  public @NotNull Map<@NotNull String, @NotNull String> getInputTypes() {
    return myInputTypes;
  }

  public @Nullable String getOutputType() {
    return myOutputType;
  }

  public @NotNull Set<@NotNull String> getGlobalWrites() {
    return myGlobalWrites;
  }

  public @NotNull Set<@NotNull String> getNonlocalWrites() {
    return myNonlocalWrites;
  }

  public boolean isYieldInside() {
    return myYieldInside;
  }

  public boolean isAsync() {
    return myAsync;
  }
}
