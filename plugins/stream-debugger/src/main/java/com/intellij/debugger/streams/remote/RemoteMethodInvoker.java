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
package com.intellij.debugger.streams.remote;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class RemoteMethodInvoker implements InvokeMethodProxy {
  private final DebugProcess myDebugProcess;
  private final EvaluationContext myContext;
  private final ObjectReference myReference;

  public RemoteMethodInvoker(@NotNull DebugProcess process, @NotNull EvaluationContext context, @NotNull ObjectReference reference) {
    myDebugProcess = process;
    myContext = context;
    myReference = reference;
  }

  @NotNull
  @Override
  public RemoteMethodInvoker call(@NotNull String name, List<? extends Value> arguments)
    throws EvaluateException {
    final Value result = evaluate(name, arguments);
    if (result instanceof ObjectReference) {
      return createInvoker(myDebugProcess, myContext, result);
    }

    throw new EvaluateException("Result must be an ObjectReference");
  }


  @Override
  @NotNull
  public Value evaluate(@NotNull String name, List<? extends Value> arguments) throws EvaluateException {
    final Type type = myReference.type();
    if (type instanceof ClassType) {
      final ClassType classType = (ClassType)type;
      final List<Method> nameMatchedMethods = classType.methodsByName(name);
      Method targetMethod = null;
      for (final Method method : nameMatchedMethods) {
        final List<String> types = method.argumentTypeNames();
        if (types.size() == arguments.size()) {
          targetMethod = method;
          break;
        }
      }

      if (targetMethod != null) {
        return myDebugProcess.invokeMethod(myContext, myReference, targetMethod, arguments);
      }
    }

    throw new EvaluateException(String.format("No method with name %s", name));
  }

  @NotNull
  @Override
  public Value getValue() {
    return myReference;
  }

  @NotNull
  private static RemoteMethodInvoker createInvoker(@NotNull DebugProcess process,
                                                   @NotNull EvaluationContext context,
                                                   @Nullable Value reference)
    throws EvaluateException {
    if (reference != null && reference instanceof ObjectReference) {
      return new RemoteMethodInvoker(process, context, (ObjectReference)reference);
    }

    throw new EvaluateException("Cannot create invoker for such value");
  }
}
