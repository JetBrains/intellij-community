// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class ResourceBundleImpl extends ResourceBundle {
  private final @NotNull SmartPsiElementPointer<PsiFile> myDefaultPropertiesFile;
  private boolean myValid = true;

  public ResourceBundleImpl(final @NotNull PropertiesFile defaultPropertiesFile) {
    myDefaultPropertiesFile = SmartPointerManager.getInstance(defaultPropertiesFile.getProject())
      .createSmartPsiElementPointer(defaultPropertiesFile.getContainingFile());
  }

  @Override
  public @NotNull List<PropertiesFile> getPropertiesFiles() {
    return PropertiesImplUtil.getResourceBundleFiles(getDefaultPropertiesFile());
  }

  @Override
  public @NotNull PropertiesFile getDefaultPropertiesFile() {
    return Objects.requireNonNull(PropertiesImplUtil.getPropertiesFile(myDefaultPropertiesFile.getElement()));
  }

  @ApiStatus.Internal
  public @Nullable VirtualFile getDefaultVirtualFile() {
    return myDefaultPropertiesFile.getVirtualFile(); // Don't resolve the pointer to avoid slow ops.
  }

  @Override
  public @NotNull String getBaseName() {
    return ResourceBundleManager.getInstance(getProject()).getBaseName(Objects.requireNonNull(myDefaultPropertiesFile.getElement()));
  }

  @Override
  public @NotNull VirtualFile getBaseDirectory() {
    return getDefaultPropertiesFile().getVirtualFile().getParent();
  }

  @Override
  public boolean isValid() {
    return myValid && PropertiesImplUtil.getPropertiesFile(myDefaultPropertiesFile.getElement()) != null;
  }

  public void invalidate() {
    myValid = false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleImpl resourceBundle = (ResourceBundleImpl)o;
    if (!myDefaultPropertiesFile.equals(resourceBundle.myDefaultPropertiesFile)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myDefaultPropertiesFile.hashCode();
  }

  public String getUrl() {
    return getBaseDirectory() + "/" + getBaseName();
  }

  @Override
  public String toString() {
    return "ResourceBundleImpl:" + getBaseName();
  }
}