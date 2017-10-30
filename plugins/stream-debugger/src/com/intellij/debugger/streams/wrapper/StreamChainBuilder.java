// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamChainBuilder {

  /**
   * Check that a chain for debugging exists (the method should be allocation-free)
   *
   * @param startElement a psi element
   * @return true if chain was found, false otherwise
   */
  boolean isChainExists(@NotNull PsiElement startElement);

  @NotNull
  List<StreamChain> build(@NotNull PsiElement startElement);
}
