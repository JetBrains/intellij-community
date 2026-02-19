// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExternalResourceManager extends SimpleModificationTracker {
  public static ExternalResourceManager getInstance() {
    return ApplicationManager.getApplication().getService(ExternalResourceManager.class);
  }

  public abstract void addResource(@NotNull @NonNls String url, @NonNls @NotNull String location);

  public abstract void addResource(@NotNull @NonNls String url, @NonNls @Nullable String version, @NotNull @NonNls String location);

  public abstract void removeResource(@NotNull String url);

  public abstract void removeResource(@NotNull String url, @Nullable String version);

  /**
   * @deprecated use {@link #getResourceLocation(String, Project)}
   */
  @Deprecated
  public abstract String getResourceLocation(@NotNull @NonNls String url);

  public abstract String getResourceLocation(@NotNull @NonNls String url, @Nullable String version);

  public abstract String getResourceLocation(@NotNull @NonNls String url, @NotNull Project project);

  public abstract @Nullable PsiFile getResourceLocation(@NotNull @NonNls String url, @NotNull PsiFile baseFile, @Nullable String version);

  public abstract String[] getResourceUrls(@Nullable FileType fileType, boolean includeStandard);

  public abstract String[] getResourceUrls(@Nullable FileType fileType, @NonNls @Nullable String version, boolean includeStandard);
}
