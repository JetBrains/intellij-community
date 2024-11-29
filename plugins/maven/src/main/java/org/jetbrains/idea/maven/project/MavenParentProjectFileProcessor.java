// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Path;

import static org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils.DEFAULT_RELATIVE_PATH;

public abstract class MavenParentProjectFileProcessor<RESULT_TYPE> {
  private final Project myProject;

  public MavenParentProjectFileProcessor(Project project) {
    myProject = project;
  }

  @Nullable
  public RESULT_TYPE process(@NotNull MavenGeneralSettings generalSettings,
                             @NotNull VirtualFile projectFile,
                             @Nullable MavenParentDesc parentDesc) {
    VirtualFile superPom = MavenUtil.getEffectiveSuperPomWithNoRespectToWrapper(myProject);
    if (superPom == null || projectFile.equals(superPom)) return null;

    RESULT_TYPE result = null;

    if (parentDesc == null) {
      return processSuperParent(superPom);
    }

    VirtualFile parentFile = findManagedFile(parentDesc.getParentId());
    if (parentFile != null) {
      result = processManagedParent(parentFile);
    }

    if (result == null && Strings.isEmpty(parentDesc.getParentRelativePath())) {
      result = findInLocalRepository(generalSettings, parentDesc);
      if (result == null) {
        parentDesc = new MavenParentDesc(parentDesc.getParentId(), DEFAULT_RELATIVE_PATH);
      }
    }

    if (result == null && projectFile.getParent() != null) {
      parentFile = projectFile.getParent().findFileByRelativePath(parentDesc.getParentRelativePath());
      if (parentFile != null && parentFile.isDirectory()) {
        parentFile = parentFile.findFileByRelativePath(MavenConstants.POM_XML);
      }
      if (parentFile != null) {
        result = processRelativeParent(parentFile);
      }
    }

    if (result == null) {
      result = findInLocalRepository(generalSettings, parentDesc);
    }

    return result;
  }

  private RESULT_TYPE findInLocalRepository(@NotNull MavenGeneralSettings generalSettings,
                                            @NotNull MavenParentDesc parentDesc) {
    RESULT_TYPE result = null;
    VirtualFile parentFile;
    Path parentIoFile = MavenArtifactUtil.getArtifactFile(generalSettings.getEffectiveRepositoryPath(),
                                                          parentDesc.getParentId(), "pom");
    parentFile = LocalFileSystem.getInstance().findFileByNioFile(parentIoFile);
    if (parentFile != null) {
      result = processRepositoryParent(parentFile);
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
