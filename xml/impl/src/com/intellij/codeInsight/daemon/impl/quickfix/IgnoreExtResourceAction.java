// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * @author mike
 */
public class IgnoreExtResourceAction extends BaseExtResourceAction {
  @Override
  protected String getQuickFixKeyId() {
    return "ignore.external.resource.text";
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  @Override
  protected void doInvoke(@NotNull final PsiFile file, final int offset, @NotNull final String uri, final Editor editor) throws IncorrectOperationException {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList(uri), null);
  }
}
