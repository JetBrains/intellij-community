// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BundleNameEvaluator {
  BundleNameEvaluator DEFAULT = new BundleNameEvaluator() {
    @Override
    public @Nullable String evaluateBundleName(final PsiFile psiFile) {
      PsiDirectory directory = ReadAction.compute(() -> psiFile.getParent());
      if (directory == null) return null;

      String packageQualifiedName = PropertiesUtil.getPackageQualifiedName(directory);
      if (packageQualifiedName == null) return null;

      StringBuilder qName = new StringBuilder(packageQualifiedName);
      if (!qName.isEmpty()) qName.append(".");
      qName.append(ResourceBundleManager.getInstance(psiFile.getProject()).getBaseName(psiFile));
      return qName.toString();
    }
  };

  BundleNameEvaluator BASE_NAME = new BundleNameEvaluator() {
    @Override
    public @NotNull String evaluateBundleName(final PsiFile psiFile) {
      return ResourceBundleManager.getInstance(psiFile.getProject()).getBaseName(psiFile);
    }
  };

  @Nullable
  String evaluateBundleName(PsiFile psiFile);
}
