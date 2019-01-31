// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class CoreExternalResourceManager extends ExternalResourceManagerEx {
  @Override
  public void removeResource(String url, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addResource(@NonNls String url, @NonNls String location, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getAvailableUrls() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getAvailableUrls(Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearAllResources() {
  }

  @Override
  public void clearAllResources(Project project) {
  }

  @Override
  public void addIgnoredResource(@NotNull String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addIgnoredResources(@NotNull List<String> urls, @Nullable Disposable disposable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isIgnoredResource(@NotNull String url) {
    return false;
  }

  @Override
  public String[] getIgnoredResources() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addExternalResourceListener(ExternalResourceListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeExternalResourceListener(ExternalResourceListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isUserResource(VirtualFile file) {
    return false;
  }

  @Override
  public boolean isStandardResource(VirtualFile file) {
    return false;
  }

  @Nullable
  @Override
  public String getUserResource(Project project, String url, String version) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getStdResource(@NotNull String url, @Nullable String version) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getDefaultHtmlDoctype(@NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDefaultHtmlDoctype(@NotNull String defaultHtmlDoctype, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public XMLSchemaVersion getXmlSchemaVersion(@NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setXmlSchemaVersion(XMLSchemaVersion version, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCatalogPropertiesFile() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCatalogPropertiesFile(@Nullable String filePath) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getModificationCount(@NotNull Project project) {
    return 0;
  }

  @Override
  public MultiMap<String, String> getUrlsByNamespace(Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addResource(@NotNull @NonNls String url, @NonNls String location) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addResource(@NotNull @NonNls String url, @NonNls String version, @NonNls String location) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeResource(@NotNull String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeResource(@NotNull String url, @Nullable String version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getResourceLocation(@NotNull @NonNls String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getResourceLocation(@NotNull @NonNls String url, @Nullable String version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getResourceLocation(@NotNull @NonNls String url, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public PsiFile getResourceLocation(@NotNull @NonNls String url, @NotNull PsiFile baseFile, String version) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getResourceUrls(@Nullable FileType fileType, boolean includeStandard) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getResourceUrls(@Nullable FileType fileType, @Nullable @NonNls String version, boolean includeStandard) {
    throw new UnsupportedOperationException();
  }
}
