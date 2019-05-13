/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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

@ApiStatus.Experimental
public abstract class YamlMetaTypeInspectionBase extends LocalInspectionTool {

  @Nullable
  protected abstract YamlMetaTypeProvider getMetaTypeProvider(@NotNull ProblemsHolder holder);

  @NotNull
  protected abstract PsiElementVisitor doBuildVisitor(@NotNull ProblemsHolder holder, @NotNull YamlMetaTypeProvider metaTypeProvider);

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    YamlMetaTypeProvider provider = getMetaTypeProvider(holder);
    return provider == null ? PsiElementVisitor.EMPTY_VISITOR
                            : doBuildVisitor(holder, provider);
  }

  protected static abstract class SimpleYamlPsiVisitor extends PsiElementVisitor {
    @Override
    public void visitElement(PsiElement element) {
      ProgressIndicatorProvider.checkCanceled();

      if (element instanceof YAMLKeyValue) {
        visitYAMLKeyValue((YAMLKeyValue)element);
      }
      else if (element instanceof YAMLMapping) {
        visitYAMLMapping((YAMLMapping)element);
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
  }
}
