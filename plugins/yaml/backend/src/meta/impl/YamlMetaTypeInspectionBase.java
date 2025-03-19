// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

@ApiStatus.Internal
public abstract class YamlMetaTypeInspectionBase extends LocalInspectionTool {

  protected abstract @Nullable YamlMetaTypeProvider getMetaTypeProvider(@NotNull ProblemsHolder holder);

  protected abstract @NotNull PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider);

  @Override
  public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    YamlMetaTypeProvider provider = getMetaTypeProvider(holder);
    return provider == null ? PsiElementVisitor.EMPTY_VISITOR
                            : doBuildVisitor(holder, provider);
  }

  protected abstract static class SimpleYamlPsiVisitor extends PsiElementVisitor {
    @Override
    public void visitElement(@NotNull PsiElement element) {
      ProgressIndicatorProvider.checkCanceled();

      if (element instanceof YAMLKeyValue) {
        visitYAMLKeyValue((YAMLKeyValue)element);
      }
      else if (element instanceof YAMLMapping) {
        visitYAMLMapping((YAMLMapping)element);
      }
      else if (element instanceof YAMLSequenceItem) {
        visitYAMLSequenceItem((YAMLSequenceItem)element);
      }
      else if (element instanceof YAMLDocument) {
        visitYAMLDocument((YAMLDocument)element);
      }
    }

    protected void visitYAMLKeyValue(@NotNull YAMLKeyValue keyValue) {
    }

    protected void visitYAMLMapping(@NotNull YAMLMapping mapping) {
    }

    protected void visitYAMLDocument(@NotNull YAMLDocument document) {
    }

    protected void visitYAMLSequenceItem(@NotNull YAMLSequenceItem item) {
    }
  }
}
