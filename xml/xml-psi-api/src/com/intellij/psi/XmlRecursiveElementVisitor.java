// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlRecursiveElementVisitor extends XmlElementVisitor implements PsiRecursiveVisitor {
  private final boolean myVisitAllFileRoots;

  public XmlRecursiveElementVisitor() {
    myVisitAllFileRoots = false;
  }

  public XmlRecursiveElementVisitor(final boolean visitAllFileRoots) {
    myVisitAllFileRoots = visitAllFileRoots;
  }

  @Override
  public void visitElement(final @NotNull PsiElement element) {
    element.acceptChildren(this);
  }

  @Override
  public void visitFile(final @NotNull PsiFile file) {
    if (myVisitAllFileRoots) {
      final FileViewProvider viewProvider = file.getViewProvider();
      final List<PsiFile> allFiles = viewProvider.getAllFiles();
      if (allFiles.size() > 1) {
        if (file == viewProvider.getPsi(viewProvider.getBaseLanguage())) {
          for (PsiFile lFile : allFiles) {
            lFile.acceptChildren(this);
          }
          return;
        }
      }
    }

    super.visitFile(file);
  }
}