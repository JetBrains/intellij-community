/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AsyncUpdateAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;

import java.util.*;

public abstract class AbstractVcsAction extends AsyncUpdateAction<VcsContext> {
  public static Collection<AbstractVcs> getActiveVcses(VcsContext dataContext) {
    Collection<AbstractVcs> result = new HashSet<AbstractVcs>();
    Collections.addAll(result, ProjectLevelVcsManager.getInstance(dataContext.getProject()).getAllActiveVcss());
    return result;
  }

  protected static FilePath[] filterDescindingFiles(FilePath[] roots, Project project) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    List<FilePath> result = new ArrayList<FilePath>(Arrays.asList(roots));
    for (FilePath first : roots) {
      for (FilePath second : roots) {
        Module firstModule = getModuleForPath(fileIndex, first);
        Module secondModule = getModuleForPath(fileIndex, second);

        if ((first != second) && (firstModule == secondModule) && VfsUtil.isAncestor(first.getIOFile(), second.getIOFile(), false)) {
          result.remove(second);
        }
      }
    }

    return result.toArray(new FilePath[result.size()]);
  }

  private static Module getModuleForPath(ProjectFileIndex fileIndex, FilePath path) {
    VirtualFile virtualFile = path.getVirtualFile();
    if (virtualFile != null) {
      return fileIndex.getModuleForFile(virtualFile);
    }
    VirtualFile virtualFileParent = path.getVirtualFileParent();
    if (virtualFileParent != null) {
      return fileIndex.getModuleForFile(virtualFileParent);
    }
    return null;
  }

  protected VcsContext prepareDataFromContext(final AnActionEvent e) {
    return VcsContextWrapper.createCachedInstanceOn(e);
  }

  protected void performUpdate(final Presentation presentation, final VcsContext data) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        update(data, presentation);
      }
    });
  }

  public final void actionPerformed(AnActionEvent e) {
    actionPerformed(VcsContextWrapper.createCachedInstanceOn(e));
  }

  protected abstract void actionPerformed(VcsContext e);

  protected abstract void update(VcsContext vcsContext, Presentation presentation);

}
