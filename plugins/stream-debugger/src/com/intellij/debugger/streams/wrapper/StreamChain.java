// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamChain {

  @NotNull
  QualifierExpression getQualifierExpression();

  @NotNull
  List<IntermediateStreamCall> getIntermediateCalls();

  @NotNull
  StreamCall getCall(int index);

  @NotNull
  TerminatorStreamCall getTerminationCall();

  @NotNull @NlsSafe String getText();

  @NotNull @NlsSafe String getCompactText();

  int length();

  @NotNull
  PsiElement getContext();
}
