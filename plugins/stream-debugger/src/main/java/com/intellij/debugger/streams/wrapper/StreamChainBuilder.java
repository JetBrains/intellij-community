package com.intellij.debugger.streams.wrapper;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamChainBuilder {
  boolean chainExists(@NotNull PsiElement startElement);

  @Nullable
  StreamChain build(@NotNull PsiElement startElement);
}
