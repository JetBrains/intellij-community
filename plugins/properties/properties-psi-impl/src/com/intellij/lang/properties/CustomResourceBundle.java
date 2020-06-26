// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public final class CustomResourceBundle extends ResourceBundle {
  private final static Logger LOG = Logger.getInstance(CustomResourceBundle.class);

  private final List<PropertiesFile> myFiles;
  private final String myBaseName;

  private CustomResourceBundle(final List<PropertiesFile> files, final @NotNull String baseName) {
    LOG.assertTrue(!files.isEmpty());
    myFiles = ContainerUtil.sorted(files, Comparator.comparing(PropertiesFile::getName));
    myBaseName = baseName;
  }

  public static CustomResourceBundle fromState(final CustomResourceBundleState state, final Project project) {
    List<PropertiesFile> files = ContainerUtil.mapNotNull(state.getFiles(VirtualFileManager.getInstance()), virtualFile -> PropertiesImplUtil.getPropertiesFile(virtualFile, project));
    return files.size() < 2 ? null : new CustomResourceBundle(files, state.getBaseName());
  }

  @NotNull
  @Override
  public List<PropertiesFile> getPropertiesFiles() {
    return myFiles;
  }

  @NotNull
  @Override
  public PropertiesFile getDefaultPropertiesFile() {
    //noinspection ConstantConditions
    return ContainerUtil.getFirstItem(myFiles);
  }

  @NotNull
  @Override
  public String getBaseName() {
    return myBaseName;
  }

  @Nullable
  @Override
  public VirtualFile getBaseDirectory() {
    VirtualFile baseDir = null;
    for (PropertiesFile file : myFiles) {
      final VirtualFile currentBaseDir = file.getContainingFile().getContainingDirectory().getVirtualFile();
      if (baseDir == null) {
        baseDir = currentBaseDir;
      } else if (!baseDir.equals(currentBaseDir)) {
        return null;
      }
    }
    return baseDir;
  }

  @Override
  public boolean isValid() {
    for (PropertiesFile file : myFiles) {
      if (!file.getContainingFile().isValid()) {
        return false;
      }
    }
    return true;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final CustomResourceBundle resourceBundle = (CustomResourceBundle)o;
    return resourceBundle.getPropertiesFiles().equals(getPropertiesFiles()) &&
           resourceBundle.getBaseName().equals(getBaseName());
  }

  public int hashCode() {
    return myFiles.hashCode() * 31 + myBaseName.hashCode();
  }
}
