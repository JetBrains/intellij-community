/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  @NotNull
  @Override
  public Collection<PsiFileSystemItem> getContexts(@NotNull Project project, @NotNull VirtualFile vFile) {
    final PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    final Module module = file != null ? ModuleUtilCore.findModuleForPsiElement(file) : null;
    if (module == null || !(file instanceof XmlFile)) return Collections.emptyList();
    final String basePath = HtmlUtil.getHrefBase((XmlFile)file);
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
