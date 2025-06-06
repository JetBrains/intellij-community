// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlRecursiveElementWalkingVisitor extends XmlElementVisitor implements PsiRecursiveVisitor {
  private final boolean myVisitAllFileRoots;
  private final PsiWalkingState myWalkingState = new PsiWalkingState(this){};

  public XmlRecursiveElementWalkingVisitor() {
    this(false);
  }

  public XmlRecursiveElementWalkingVisitor(final boolean visitAllFileRoots) {
    myVisitAllFileRoots = visitAllFileRoots;
  }

  @Override
  public void visitElement(final @NotNull PsiElement element) {
    myWalkingState.elementStarted(element);
  }

  @Override
  public void visitFile(final @NotNull PsiFile psiFile) {
    if (myVisitAllFileRoots) {
      final FileViewProvider viewProvider = psiFile.getViewProvider();
      final List<PsiFile> allFiles = viewProvider.getAllFiles();
      if (allFiles.size() > 1) {
        if (psiFile == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
          for (PsiFile lFile : allFiles) {
            lFile.acceptChildren(this);
          }
          return;
        }
      }
    }

    super.visitFile(psiFile);
  }

  public void stopWalking() {
    myWalkingState.stopWalking();
  }

}