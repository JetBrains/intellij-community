// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ExternalResourceManagerEx extends ExternalResourceManager {
  @NonNls public static final String STANDARD_SCHEMAS = "/standardSchemas/";

  public enum XMLSchemaVersion {
    XMLSchema_1_0,
    XMLSchema_1_1
  }

  public static ExternalResourceManagerEx getInstanceEx() {
    return (ExternalResourceManagerEx)getInstance();
  }

  public abstract void removeResource(String url, @NotNull Project project);

  public abstract void addResource(@NonNls String url, @NonNls String location, @NotNull Project project);

  public abstract String[] getAvailableUrls();

  public abstract String[] getAvailableUrls(Project project);

  public abstract void clearAllResources();

  public abstract void clearAllResources(Project project);

  /**
   * @deprecated Use {@link #addIgnoredResources(List, Disposable)}
   */
  @Deprecated
  public abstract void addIgnoredResource(@NotNull String url);

  public abstract void addIgnoredResources(@NotNull List<String> urls, @Nullable Disposable disposable);

  public abstract boolean isIgnoredResource(@NotNull String url);

  public abstract String[] getIgnoredResources();

  public abstract void addExternalResourceListener(ExternalResourceListener listener);

  public abstract void removeExternalResourceListener(ExternalResourceListener listener);

  public abstract boolean isUserResource(VirtualFile file);

  public abstract boolean isStandardResource(VirtualFile file);

  @Nullable
  public abstract String getUserResource(Project project, String url, String version);

  @Nullable
  public abstract String getStdResource(@NotNull String url, @Nullable String version);

  @NotNull
  public abstract String getDefaultHtmlDoctype(@NotNull Project project);

  public abstract void setDefaultHtmlDoctype(@NotNull String defaultHtmlDoctype, @NotNull Project project);

  public abstract XMLSchemaVersion getXmlSchemaVersion(@NotNull Project project);

  public abstract void setXmlSchemaVersion(XMLSchemaVersion version, @NotNull Project project);

  public abstract String getCatalogPropertiesFile();

  public abstract void setCatalogPropertiesFile(@Nullable String filePath);

  public abstract long getModificationCount(@NotNull Project project);

  public abstract MultiMap<String, String> getUrlsByNamespace(Project project);
}
