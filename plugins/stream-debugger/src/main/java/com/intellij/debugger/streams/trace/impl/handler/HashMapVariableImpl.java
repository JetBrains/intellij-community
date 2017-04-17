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

import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
class HashMapVariableImpl extends VariableImpl {
  private final GenericType myKeyType;
  private final GenericType myValueType;

  HashMapVariableImpl(@NotNull String name, @NotNull GenericType from, @NotNull GenericType to, boolean isLinked) {
    super(String.format("java.util.Map<%s, %s>", from.getGenericTypeName(), to.getGenericTypeName()), name,
          isLinked ? "new java.util.LinkedHashMap<>()" : "new java.util.HashMap<>()");
    myKeyType = from;
    myValueType = to;
  }

  @NotNull
  GenericType getKeyType() {
    return myKeyType;
  }

  @NotNull
  GenericType getValueType() {
    return myValueType;
  }

  @NotNull
  String convertToArray(@NotNull String arrayName) {
    final String newLine = TraceExpressionBuilderImpl.LINE_SEPARATOR;

    final String keysType = myKeyType.getVariableTypeName();
    final String valuesType = myValueType.getVariableTypeName();

    return "final java.lang.Object[] " + arrayName + ";" + newLine +
           "{" + newLine +
           "  final int size = " + getName() + ".size();" + newLine +
           "  final " + keysType + "[] keys = new " + keysType + "[size];" + newLine +
           "  final " + valuesType + "[] values = new " + valuesType + "[size];" + newLine +
           "  int i = 0;" + newLine +
           "  for (final " + keysType + String.format(" key : %s.keySet()) {", getName()) + newLine +
           "    final " + valuesType + " value = " + String.format("%s.get(key);", getName()) + newLine +
           "    keys[i] = key;" + newLine +
           "    values[i] = value;" + newLine +
           "    i++;" + newLine +
           "  }" + newLine +
           "  " + arrayName + " = new java.lang.Object[] { keys, values };" + newLine +
           "}" + newLine;
  }
}
