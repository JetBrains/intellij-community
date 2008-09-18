package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
public class ModuleVcsPathPresenter extends VcsPathPresenter {
  private Project myProject;

  public ModuleVcsPathPresenter(final Project project) {
    myProject = project;
  }

  public String getPresentableRelativePathFor(final VirtualFile file) {
    if (file == null) return "";
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject)
          .getFileIndex();
        Module module = fileIndex.getModuleForFile(file);
        VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
        if (module == null || contentRoot == null) return file.getPresentableUrl();
        StringBuffer result = new StringBuffer();
        result.append("[");
        result.append(module.getName());
        result.append("] ");
        result.append(contentRoot.getName());
        String relativePath = VfsUtil.getRelativePath(file, contentRoot, File.separatorChar);
        if (relativePath.length() > 0) {
          result.append(File.separatorChar);
          result.append(relativePath);
        }
        return result.toString();
      }
    });
  }

  public String getPresentableRelativePath(@NotNull final ContentRevision fromRevision, @NotNull final ContentRevision toRevision) {
    // need to use parent path because the old file is already not there
    FilePath fromPath = fromRevision.getFile();
    FilePath toPath = toRevision.getFile();

    if (fromPath == null || toPath == null || (fromPath.getParentPath() == null) || (toPath.getParentPath() == null)) {
      return null;
    }

    final VirtualFile oldFile = fromPath.getParentPath().getVirtualFile();
    final VirtualFile newFile = toPath.getParentPath().getVirtualFile();
    if (oldFile != null && newFile != null) {
      Module oldModule = ModuleUtil.findModuleForFile(oldFile, myProject);
      Module newModule = ModuleUtil.findModuleForFile(newFile, myProject);
      if (oldModule != newModule) {
        return getPresentableRelativePathFor(oldFile);
      }
    }
    if (toPath.getIOFile() == null || fromPath.getIOFile() == null) {
      return null;
    }
    final RelativePathCalculator calculator =
      new RelativePathCalculator(toPath.getIOFile().getAbsolutePath(), fromPath.getIOFile().getAbsolutePath());
    calculator.execute();
    final String result = calculator.getResult();
    return (result == null) ? null : result.replace("/", File.separator);
  }

}