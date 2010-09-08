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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
public abstract class ExternalResourceManagerEx extends ExternalResourceManager {
  public static ExternalResourceManagerEx getInstanceEx(){
    return (ExternalResourceManagerEx)getInstance();
  }

  public abstract void removeResource(String url, @NotNull Project project);

  public abstract void addResource(@NonNls String url, @NonNls String location, @NotNull Project project);

  public abstract String[] getAvailableUrls();
  public abstract String[] getAvailableUrls(Project project);

  public abstract void clearAllResources();
  public abstract void clearAllResources(Project project);

  public abstract void addIgnoredResource(String url);
  public abstract void removeIgnoredResource(String url);

  public abstract boolean isIgnoredResource(String url);

  public abstract String[] getIgnoredResources();

  public abstract void addExternalResourceListener(ExternalResourceListener listener);

  public abstract void removeExternalResourceListener(ExternalResourceListener listener);

  @Nullable
  public abstract String getUserResourse(Project project, String url, String version);
  @Nullable
  public abstract String getStdResource(String url, String version);

  @NotNull
  public abstract String getDefaultHtmlDoctype(@NotNull Project project);

  public abstract void setDefaultHtmlDoctype(@NotNull String defaultHtmlDoctype, @NotNull Project project);

  public abstract long getModificationCount(@NotNull Project project);
}
