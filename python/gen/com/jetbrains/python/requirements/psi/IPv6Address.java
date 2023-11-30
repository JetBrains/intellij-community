// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface IPv6Address extends PsiElement {

  @Nullable
  H16 getH16();

  @NotNull
  List<H16Colon> getH16ColonList();

  @Nullable
  Ls32 getLs32();

}
