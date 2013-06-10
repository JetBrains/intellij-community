/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.javaee;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
public abstract class ExternalResourceManager implements ModificationTracker {

  public static ExternalResourceManager getInstance() {
    return ServiceManager.getService(ExternalResourceManager.class);
  }

  public abstract void addResource(@NonNls String url, @NonNls String location);
  public abstract void addResource(@NonNls String url, @NonNls String version, @NonNls String location);

  public abstract void removeResource(String url);
  public abstract void removeResource(String url, String version);

  /** @see #getResourceLocation(String, com.intellij.openapi.project.Project) */
  @Deprecated
  public abstract String getResourceLocation(@NonNls String url);
  public abstract String getResourceLocation(@NonNls String url, String version);

  public abstract String getResourceLocation(@NonNls String url, @NotNull Project project);

  @Nullable
  public abstract PsiFile getResourceLocation(@NotNull @NonNls String url, @NotNull PsiFile baseFile, String version);

  public abstract String[] getResourceUrls(@Nullable FileType fileType, final boolean includeStandard);
  public abstract String[] getResourceUrls(@Nullable FileType fileType, @NonNls String version, final boolean includeStandard);
}
