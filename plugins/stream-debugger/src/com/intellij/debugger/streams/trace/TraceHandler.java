// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.trace.dsl.CodeBlock;
import com.intellij.debugger.streams.trace.dsl.Expression;
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceHandler {
  @NotNull
  List<VariableDeclaration> additionalVariablesDeclaration();

  @NotNull
  CodeBlock prepareResult();

  @NotNull
  Expression getResultExpression();
}
