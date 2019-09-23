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

import java.util.Set;

/**
 * @author vlan
 */
public class PyCodeFragment extends CodeFragment {
  private final Set<String> myGlobalWrites;
  private final Set<String> myNonlocalWrites;
  private final boolean myYieldInside;
  private final boolean myAsync;

  public PyCodeFragment(final Set<String> input,
                        final Set<String> output,
                        final Set<String> globalWrites,
                        final Set<String> nonlocalWrites,
                        final boolean returnInside,
                        final boolean yieldInside,
                        final boolean isAsync) {
    super(input, output, returnInside);
    myGlobalWrites = globalWrites;
    myNonlocalWrites = nonlocalWrites;
    myYieldInside = yieldInside;
    myAsync = isAsync;
  }

  public Set<String> getGlobalWrites() {
    return myGlobalWrites;
  }

  public Set<String> getNonlocalWrites() {
    return myNonlocalWrites;
  }

  public boolean isYieldInside() {
    return myYieldInside;
  }

  public boolean isAsync() {
    return myAsync;
  }
}
