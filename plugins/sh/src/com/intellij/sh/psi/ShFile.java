// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.ShTypes;
import com.intellij.sh.codeInsight.processor.ShFunctionDeclarationProcessor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
    return lool(this, place.getTextRange(), processor, state);
  }

  private boolean lool(@Nullable PsiElement element, TextRange lastParent, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
    if (element == null) return true;
    for (PsiElement e = element; e != null; e = e.getPrevSibling()) {
      if (!e.getTextRange().contains(lastParent) && e.getTextRange().getEndOffset() > lastParent.getStartOffset()) continue;
      if (!processor.execute(e, state) || (shouldGoDeeper(e) && !lool(e.getLastChild(), lastParent, processor, state))) return false;
    }
    return true;
  }

  private static boolean shouldGoDeeper(@NotNull PsiElement element) {
    return !(element instanceof ShFunctionDefinition);
  }

  public Map<PsiElement, MultiMap<String, ShFunctionName>> getFunctionsDeclarationWithScope() {
    return CachedValuesManager.getCachedValue(this, () ->
      CachedValueProvider.Result.create(calculateFunctionsDeclaration(), this));
  }

  @Nullable
  public String findShebang() {
    return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result.create(findShebangInner(), this));
  }

  @NotNull
  private Map<PsiElement, MultiMap<String, ShFunctionName>> calculateFunctionsDeclaration() {
    ShFunctionDeclarationProcessor processor = new ShFunctionDeclarationProcessor();
    processDeclarations(processor, ResolveState.initial(), null, this);
    return processor.getFunctionsDeclarationWithScope();
  }

  @Nullable
  private String findShebangInner() {
    ASTNode shebang = getNode().findChildByType(ShTypes.SHEBANG);
    return shebang != null ? shebang.getText() : null;
  }
}