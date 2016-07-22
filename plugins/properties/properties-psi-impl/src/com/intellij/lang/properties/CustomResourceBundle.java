/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class CustomResourceBundle extends ResourceBundle {
  private final static Logger LOG = Logger.getInstance(CustomResourceBundle.class);

  private final List<PropertiesFile> myFiles;
  private final String myBaseName;

  private CustomResourceBundle(final List<PropertiesFile> files, final @NotNull String baseName) {
    LOG.assertTrue(!files.isEmpty());
    myFiles = new ArrayList<>(files);
    Collections.sort(myFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
    myBaseName = baseName;
  }

  public static CustomResourceBundle fromState(final CustomResourceBundleState state, final Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final List<PropertiesFile> files =
      ContainerUtil.map(state.getFiles(VirtualFileManager.getInstance()), virtualFile -> PropertiesImplUtil.getPropertiesFile(psiManager.findFile(virtualFile)));
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
