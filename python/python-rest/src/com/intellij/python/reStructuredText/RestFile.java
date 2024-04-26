// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.PsiFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestFile extends PsiFileImpl {
  public RestFile(FileViewProvider viewProvider) {
    super(RestElementTypes.REST_FILE, RestElementTypes.REST_FILE, viewProvider);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return RestFileType.INSTANCE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @Override
  public String toString() {
    return "rest file";
  }

}
