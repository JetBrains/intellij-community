// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.wrapper.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class StreamChainImpl implements StreamChain {
  private final QualifierExpression myQualifierExpression;
  private final List<? extends IntermediateStreamCall> myIntermediateCalls;
  private final TerminatorStreamCall myTerminator;
  private final PsiElement myContext;

  public StreamChainImpl(@NotNull QualifierExpression qualifierExpression,
                         @NotNull List<? extends IntermediateStreamCall> intermediateCalls,
                         @NotNull TerminatorStreamCall terminator,
                         @NotNull PsiElement context) {
    myQualifierExpression = qualifierExpression;
    myIntermediateCalls = intermediateCalls;
    myTerminator = terminator;
    myContext = context;
  }

  @Override
  public @NotNull QualifierExpression getQualifierExpression() {
    return myQualifierExpression;
  }

  @Override
  public @NotNull List<IntermediateStreamCall> getIntermediateCalls() {
    return Collections.unmodifiableList(myIntermediateCalls);
  }

  @Override
  public @NotNull StreamCall getCall(int index) {
    if (0 <= index && index < length()) {
      return doGetCall(index);
    }

    throw new IndexOutOfBoundsException("Call index out of bound: " + index);
  }

  @Override
  public @NotNull TerminatorStreamCall getTerminationCall() {
    return myTerminator;
  }

  @Override
  public @NotNull @NlsSafe String getText() {
    final Iterator<StreamCall> iterator = StreamEx.of(myIntermediateCalls).map(x -> (StreamCall)x).append(myTerminator).iterator();
    final StringBuilder builder = new StringBuilder();
    builder.append(myQualifierExpression.getText()).append("\n").append(".");

    while (iterator.hasNext()) {
      final MethodCall call = iterator.next();
      final String args = args2Text(call.getArguments());
      builder.append(call.getName()).append(args);
      if (iterator.hasNext()) {
        builder.append("\n").append(".");
      }
    }

    return builder.toString();
  }


  @Override
  public @NotNull @NlsSafe String getCompactText() {
    final StringBuilder builder = new StringBuilder();
    builder.append(myQualifierExpression.getText().replaceAll("\\s+", ""));
    for (final StreamCall call : StreamEx.of(myIntermediateCalls).map(x -> (StreamCall)x).append(myTerminator)) {
      builder.append(" -> ").append(call.getName());
    }

    return builder.toString();
  }

  @Override
  public int length() {
    return 1 + myIntermediateCalls.size();
  }

  @Override
  public @NotNull PsiElement getContext() {
    return myContext;
  }

  private StreamCall doGetCall(int index) {
    if (index < myIntermediateCalls.size()) {
      return myIntermediateCalls.get(index);
    }

    return myTerminator;
  }

  private static @NotNull String args2Text(@NotNull List<CallArgument> args) {
    return StreamEx.of(args).map(CallArgument::getText).joining(", ", "(", ")");
  }
}
