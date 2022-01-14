// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.sh.psi.ShIncludeCommand;
import com.intellij.sh.psi.ShSimpleCommandElement;
import com.intellij.sh.psi.ShString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class ShIncludeCommandMixin extends ShCommandImpl implements ShIncludeCommand {
  ShIncludeCommandMixin(ASTNode node) {
    super(node);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    List<ShSimpleCommandElement> commandsList = getSimpleCommandElementList();
    if (commandsList.size() <= 0) return true;
    ShSimpleCommandElement simpleCommandElement = commandsList.get(0);
    PsiFile includedPsiFile = getReferencingFile(simpleCommandElement);
    if (includedPsiFile == null) return true;
    VirtualFile sourceFile = place.getContainingFile().getVirtualFile();
    if (includedPsiFile.getVirtualFile().equals(sourceFile)) return true;
    return includedPsiFile.processDeclarations(processor, state, this, place);
  }

  public @Nullable PsiFile getReferencingFile(@NotNull PsiElement element) {
    String relativeFilePath = element.getText();
    if (element instanceof ShString) {
      ShString shString = (ShString)element;
      if (relativeFilePath.length() >= 2 &&
          (shString.getOpenQuote() != null && shString.getCloseQuote() != null) ||
          (shString.getRawString() != null && relativeFilePath.startsWith("'") && relativeFilePath.endsWith("'"))) {
        relativeFilePath = relativeFilePath.substring(1, relativeFilePath.length() - 1);
      }
    } else {
      if (relativeFilePath.contains("\\ ")) {
        relativeFilePath = relativeFilePath.replace("\\ ", " ");
      }
    }

    PsiDirectory containingDirectory = getContainingFile().getContainingDirectory();
    if (containingDirectory == null) return null;
    VirtualFile relativeFile = VfsUtilCore.findRelativeFile(relativeFilePath, containingDirectory.getVirtualFile());
    if (relativeFile == null) return null;
    return PsiManager.getInstance(getProject()).findFile(relativeFile);
  }
}
