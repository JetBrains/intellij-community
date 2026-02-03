// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;


import com.intellij.psi.meta.PsiPresentableMetaData;

/**
 * Defines a strategy for rendering metadata from {@link PsiPresentableMetaData}.
 */
public interface PsiPresentableMetaDataRenderStrategy extends PsiPresentableMetaData {
  boolean isRenderExpensive();
}
