package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.io.File;

public abstract class MavenParentProjectFileProcessor<RESULT_TYPE> {
  @Nullable
  public RESULT_TYPE process(@NotNull VirtualFile projectFile,
                             @NotNull MavenId parentId,
                             @NotNull String parentRelativePath,
                             @NotNull File localRepository) {
    VirtualFile parentFile = findManagedFile(parentId);
    RESULT_TYPE result = null;
    if (parentFile != null) {
      result = processManagedParent(parentFile);
    }

    if (result == null) {
      parentFile = projectFile.getParent().findFileByRelativePath(parentRelativePath);
      if (parentFile != null && parentFile.isDirectory()) {
        parentFile = parentFile.findFileByRelativePath(MavenConstants.POM_XML);
      }
      if (parentFile != null) {
        result = processRelativeParent(parentFile);
      }
    }

    if (result == null) {
      File parentIoFile = MavenArtifactUtil.getArtifactFile(localRepository, parentId, "pom");
      parentFile = LocalFileSystem.getInstance().findFileByIoFile(parentIoFile);
      if (parentFile != null) {
        result = processRepositoryParent(parentFile);
      }
    }
    return result;
  }

  protected abstract VirtualFile findManagedFile(MavenId id);

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
  protected abstract RESULT_TYPE doProcessParent(VirtualFile parentFile);
}
