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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.LibrariesElement;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PackageViewLibrariesNode extends ProjectViewNode<LibrariesElement>{
  public PackageViewLibrariesNode(final Project project, Module module, final ViewSettings viewSettings) {
    super(project, new LibrariesElement(module, project), viewSettings);
  }

  public boolean contains(final VirtualFile file) {
    return someChildContainsFile(file);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (getValue().getModule() == null) {
      final Module[] modules = ModuleManager.getInstance(getProject()).getModules();

      for (int i = 0; i < modules.length; i++) {
        Module module = modules[i];
        addModuleLibraryRoots(ModuleRootManager.getInstance(module), roots);
      }

    } else {
      addModuleLibraryRoots(ModuleRootManager.getInstance(getValue().getModule()), roots);
    }
    return PackageUtil.createPackageViewChildrenOnFiles(roots, getProject(), getSettings(), null, true);
  }

  private static void addModuleLibraryRoots(ModuleRootManager moduleRootManager, List<VirtualFile> roots) {
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (int idx = 0; idx < orderEntries.length; idx++) {
      final OrderEntry orderEntry = orderEntries[idx];
      if (!(orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry)) {
        continue;
      }
      final VirtualFile[] files = orderEntry.getFiles(OrderRootType.CLASSES);
      for (int i = 0; i < files.length; i++) {
        final VirtualFile file = files[i];
        if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) {
          // skip entries inside jars
          continue;
        }
        roots.add(file);
      }
    }
  }

  public void update(final PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.libraries"));
    presentation.setIcons(Icons.LIBRARY_ICON);
  }

  public String getTestPresentation() {
    return "Libraries";
  }

  public boolean shouldUpdateData() {
    return true;
  }

  public int getWeight() {
    return 60;
  }
}
