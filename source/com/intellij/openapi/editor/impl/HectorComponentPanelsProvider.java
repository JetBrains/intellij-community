/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.editor.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class HectorComponentPanelsProvider {
  public static HectorComponentPanelsProvider[] getProviders(Project project) {
    return project.getComponents(HectorComponentPanelsProvider.class);
  }

  public abstract @Nullable HectorComponentPanel createPanel(@NotNull PsiFile file);

}
