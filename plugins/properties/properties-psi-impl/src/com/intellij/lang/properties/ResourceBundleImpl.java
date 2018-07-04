/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/**
 * @author Alexey
 */
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ResourceBundleImpl extends ResourceBundle {
  @NotNull
  private final SmartPsiElementPointer<PsiFile> myDefaultPropertiesFile;
  private boolean myValid = true;

  public ResourceBundleImpl(@NotNull final PropertiesFile defaultPropertiesFile) {
    myDefaultPropertiesFile = SmartPointerManager.getInstance(defaultPropertiesFile.getProject()).createSmartPsiElementPointer(defaultPropertiesFile.getContainingFile());
  }

  @NotNull
  @Override
  public List<PropertiesFile> getPropertiesFiles() {
    return PropertiesImplUtil.getResourceBundleFiles(getDefaultPropertiesFile());
  }

  @NotNull
  @Override
  public PropertiesFile getDefaultPropertiesFile() {
    return Objects.requireNonNull(PropertiesImplUtil.getPropertiesFile(myDefaultPropertiesFile.getElement()));
  }

  @NotNull
  @Override
  public String getBaseName() {
    return ResourceBundleManager.getInstance(getProject()).getBaseName(Objects.requireNonNull(myDefaultPropertiesFile.getElement()));
  }

  @NotNull
  public VirtualFile getBaseDirectory() {
    return getDefaultPropertiesFile().getParent().getVirtualFile();
  }

  @Override
  public boolean isValid() {
    return myValid && myDefaultPropertiesFile.getElement() != null;
  }

  public void invalidate() {
    myValid = false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleImpl resourceBundle = (ResourceBundleImpl)o;
    if (!myDefaultPropertiesFile.equals(resourceBundle.myDefaultPropertiesFile)) return false;
    return true;
  }

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