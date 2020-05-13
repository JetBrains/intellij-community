// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.ShTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFile extends PsiFileBase {
  public ShFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, ShLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ShFileType.INSTANCE;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, state, lastParent, place);
  }

  @Nullable
  public String findShebang() {
    return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result.create(findShebangInner(), this));
  }

  @Nullable
  private String findShebangInner() {
    ASTNode shebang = getNode().findChildByType(ShTypes.SHEBANG);
    return shebang != null ? shebang.getText() : null;
  }
}