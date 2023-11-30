// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface NameReq extends PsiElement {

  @Nullable
  Extras getExtras();

  @NotNull
  List<HashOption> getHashOptionList();

  @Nullable
  QuotedMarker getQuotedMarker();

  @NotNull
  SimpleName getSimpleName();

  @Nullable
  Versionspec getVersionspec();

}
