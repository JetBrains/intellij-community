// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface Option extends PsiElement {

  @Nullable
  ConstraintReq getConstraintReq();

  @Nullable
  EditableReq getEditableReq();

  @Nullable
  ExtraIndexUrlReq getExtraIndexUrlReq();

  @Nullable
  FindLinksReq getFindLinksReq();

  @Nullable
  IndexUrlReq getIndexUrlReq();

  @Nullable
  NoBinaryReq getNoBinaryReq();

  @Nullable
  NoIndexReq getNoIndexReq();

  @Nullable
  OnlyBinaryReq getOnlyBinaryReq();

  @Nullable
  PreReq getPreReq();

  @Nullable
  PreferBinaryReq getPreferBinaryReq();

  @Nullable
  ReferReq getReferReq();

  @Nullable
  RequireHashesReq getRequireHashesReq();

  @Nullable
  TrustedHostReq getTrustedHostReq();

  @Nullable
  UseFeatureReq getUseFeatureReq();

}
