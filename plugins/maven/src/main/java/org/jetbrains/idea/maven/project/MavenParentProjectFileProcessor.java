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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;

import java.io.File;

public abstract class MavenParentProjectFileProcessor<RESULT_TYPE> {
  @Nullable
  public RESULT_TYPE process(@NotNull MavenGeneralSettings generalSettings,
                             @NotNull VirtualFile projectFile,
                             @Nullable MavenParentDesc parentDesc) {
    VirtualFile superPom = generalSettings.getEffectiveSuperPom();
    if (projectFile == superPom) return null;

    RESULT_TYPE result = null;

    if (parentDesc == null) {
      return processSuperParent(superPom);
    }

    VirtualFile parentFile = findManagedFile(parentDesc.getParentId());
    if (parentFile != null) {
      result = processManagedParent(parentFile);
    }

    if (result == null) {
      parentFile = projectFile.getParent().findFileByRelativePath(parentDesc.getParentRelativePath());
      if (parentFile != null && parentFile.isDirectory()) {
        parentFile = parentFile.findFileByRelativePath(MavenConstants.POM_XML);
      }
      if (parentFile != null) {
        result = processRelativeParent(parentFile);
      }
    }

    if (result == null) {
      File parentIoFile = MavenArtifactUtil.getArtifactFile(generalSettings.getEffectiveLocalRepository(),
                                                            parentDesc.getParentId(), "pom");
      parentFile = LocalFileSystem.getInstance().findFileByIoFile(parentIoFile);
      if (parentFile != null) {
        result = processRepositoryParent(parentFile);
      }
    }

    return result;
  }

  @Nullable
  protected abstract VirtualFile findManagedFile(@NotNull MavenId id);

  @Nullable
  protected RESULT_TYPE processManagedParent(VirtualFile parentFile) {
    return doProcessParent(parentFile);
  }

  @Nullable
  protected RESULT_TYPE processRelativeParent(VirtualFile parentFile) {
    return doProcessParent(parentFile);
  }

  @Nullable
  protected RESULT_TYPE processRepositoryParent(VirtualFile parentFile) {
    return doProcessParent(parentFile);
  }

  @Nullable
  protected RESULT_TYPE processSuperParent(VirtualFile parentFile) {
    return doProcessParent(parentFile);
  }

  @Nullable
  protected abstract RESULT_TYPE doProcessParent(VirtualFile parentFile);
}
