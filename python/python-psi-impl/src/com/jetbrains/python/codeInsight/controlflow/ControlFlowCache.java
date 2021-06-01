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
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.openapi.util.Key;
import com.intellij.reference.SoftReference;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeImpl;
import org.jetbrains.annotations.NotNull;


public final class ControlFlowCache {
  private static final Key<SoftReference<ControlFlow>> CONTROL_FLOW_KEY = Key.create("com.jetbrains.python.codeInsight.controlflow.ControlFlow");
  private static final Key<SoftReference<Scope>> SCOPE_KEY = Key.create("com.jetbrains.python.codeInsight.controlflow.Scope");

  private ControlFlowCache() {
  }

  public static void clear(ScopeOwner scopeOwner) {
    scopeOwner.putUserData(CONTROL_FLOW_KEY, null);
    scopeOwner.putUserData(SCOPE_KEY, null);
  }

  @NotNull
  public static ControlFlow getControlFlow(@NotNull ScopeOwner element,
                                           @NotNull PyControlFlowBuilder controlFlowBuilder) {
    SoftReference<ControlFlow> ref = element.getUserData(CONTROL_FLOW_KEY);
    ControlFlow flow = SoftReference.dereference(ref);
    if (flow == null) {
      flow = controlFlowBuilder.buildControlFlow(element);
      element.putUserData(CONTROL_FLOW_KEY, new SoftReference<>(flow));
    }
    return flow;
  }

  @NotNull
  public static ControlFlow getControlFlow(@NotNull ScopeOwner element) {
    return getControlFlow(element, new PyControlFlowBuilder());
  }

  @NotNull
  public static Scope getScope(@NotNull ScopeOwner element) {
    SoftReference<Scope> ref = element.getUserData(SCOPE_KEY);
    Scope scope = SoftReference.dereference(ref);
    if (scope == null) {
      scope = new ScopeImpl(element);
      element.putUserData(SCOPE_KEY, new SoftReference<>(scope));
    }
    return scope;
  }
}
