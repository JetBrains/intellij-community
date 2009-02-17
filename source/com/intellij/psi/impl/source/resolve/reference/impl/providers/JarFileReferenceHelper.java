package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class JarFileReferenceHelper implements FileReferenceHelper {
  @NotNull
  public String trimUrl(@NotNull String url) {
    return url;
  }

  @NotNull
  public String getDirectoryTypeName() {
    return "";
  }

  public List<? extends LocalQuickFix> registerFixes(HighlightInfo info, FileReference reference) {
    return Collections.emptyList();
  }

  public PsiFileSystemItem getPsiFileSystemItem(Project project, @NotNull VirtualFile file) {
    return null;
  }

  public PsiFileSystemItem findRoot(Project project, @NotNull VirtualFile file) {
    return null;
  }

  @NotNull
  public Collection<PsiFileSystemItem> getRoots(@NotNull Module module) {
    PsiPackage psiPackage = JavaPsiFacade.getInstance(module.getProject()).findPackage("");
    if (psiPackage != null) {
      return Arrays.<PsiFileSystemItem>asList(psiPackage.getDirectories());
    }
    return Collections.emptyList();
  }

  @NotNull
  public Collection<PsiFileSystemItem> getContexts(Project project, @NotNull VirtualFile file) {
    return Collections.emptyList();
  }

  public boolean isMine(Project project, @NotNull VirtualFile file) {
    return ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(file);
  }
}
