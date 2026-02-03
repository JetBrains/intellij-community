// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Dennis.Ushakov
 */
public class HtmlFileReferenceHelper extends FileReferenceHelper {
  @Override
  public @NotNull Collection<PsiFileSystemItem> getContexts(@NotNull Project project, @NotNull VirtualFile vFile) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
    final Module module = psiFile != null ? ModuleUtilCore.findModuleForPsiElement(psiFile) : null;
    if (module == null || !(psiFile instanceof XmlFile)) return Collections.emptyList();
    final String basePath = HtmlUtil.getHrefBase((XmlFile)psiFile);
    if (basePath != null && !HtmlUtil.hasHtmlPrefix(basePath)) {
      for (VirtualFile virtualFile : getBaseRoots(module)) {
        final VirtualFile base = virtualFile.findFileByRelativePath(basePath);
        final PsiDirectory result = base != null ? PsiManager.getInstance(project).findDirectory(base) : null;
        if (result != null) {
          return Collections.singletonList(result);
        }
      }
    }
    return Collections.emptyList();
  }

  protected Collection<VirtualFile> getBaseRoots(final Module module) {
    return Arrays.asList(ModuleRootManager.getInstance(module).getContentRoots());
  }

  @Override
  public boolean isMine(@NotNull Project project, @NotNull VirtualFile file) {
    if (!ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) return false;

    final FileType fileType = file.getFileType();
    return fileType == HtmlFileType.INSTANCE || fileType == XHtmlFileType.INSTANCE;
  }
}
