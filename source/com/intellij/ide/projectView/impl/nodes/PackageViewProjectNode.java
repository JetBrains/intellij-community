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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiDirectory;

import java.util.*;

public class PackageViewProjectNode extends AbstractProjectNode {
  public PackageViewProjectNode(Project project, ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildren() {
    if (getSettings().isShowModules()) {
      final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
      return modulesAndGroups(modules);
    }
    else {
      final List<VirtualFile> sourceRoots = new ArrayList<VirtualFile>();
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
      sourceRoots.addAll(Arrays.asList(projectRootManager.getContentSourceRoots()));

      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
      final Set<PsiPackage> topLevelPackages = new HashSet<PsiPackage>();
      final ProjectFileIndex projectFileIndex = projectRootManager.getFileIndex();

      for (Iterator<VirtualFile> it = sourceRoots.iterator(); it.hasNext();) {
        final VirtualFile root = it.next();
        final PsiDirectory directory = psiManager.findDirectory(root);
        if (directory == null) {
        continue;
        }
        final PsiPackage directoryPackage = directory.getPackage();
        if (directoryPackage == null || PackageUtil.isPackageDefault(directoryPackage)) {
          // add subpackages
          final PsiDirectory[] subdirectories = directory.getSubdirectories();
          for (int i = 0; i < subdirectories.length; i++) {
            final PsiPackage aPackage = subdirectories[i].getPackage();
            if (aPackage != null && !PackageUtil.isPackageDefault(aPackage)) {
              topLevelPackages.add(aPackage);
            }
          }
          // add non-dir items
          children.addAll(PackageUtil.getDirectoryChildren(directory, getSettings(), false));
        }
        else {
          // this is the case when a source root has pakage prefix assigned
          topLevelPackages.add(directoryPackage);
        }
      }

      for (Iterator<PsiPackage> it = topLevelPackages.iterator(); it.hasNext();) {
        PackageUtil.addPackageAsChild(children, it.next(), null, getSettings(), false);
      }

      if (getSettings().isShowLibraryContents()) {
        children.add(new PackageViewLibrariesNode(getProject(), null, getSettings()));
      }

      return children;
    }


  }

  protected Class<? extends AbstractTreeNode> getModuleNodeClass() {
    return PackageViewModuleNode.class;
  }
}
