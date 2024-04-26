// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyContainerProvider implements ContainerProvider {
  @Override
  public @Nullable PsiElement getContainer(@NotNull PsiElement item) {
    if (item instanceof PyElement && item instanceof StubBasedPsiElement) {
      return getContainerByStub((StubBasedPsiElement)item);
    }
    return null;
  }

  private static @Nullable PsiElement getContainerByStub(@NotNull StubBasedPsiElement element) {
    final StubElement stub = element.getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub != null) {
        return parentStub.getPsi();
      }
    }
    return null;
  }
}
