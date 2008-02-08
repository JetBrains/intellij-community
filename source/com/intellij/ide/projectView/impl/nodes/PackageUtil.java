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

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PackageUtil {

  public static PsiPackage[] getSubpackages(PsiPackage aPackage,
                                            Module module,
                                            final Project project,
                                            final boolean searchInLibraries) {
    final GlobalSearchScope scopeToShow = getScopeToShow(project, module, searchInLibraries);
    final PsiDirectory[] dirs = aPackage.getDirectories(scopeToShow);
    final Set<PsiPackage> subpackages = new HashSet<PsiPackage>();
    for (PsiDirectory dir : dirs) {
      final PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (PsiDirectory subdirectory : subdirectories) {
        final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(subdirectory);
        if (psiPackage != null) {
          final String name = psiPackage.getName();
          // skip "default" subpackages as they should be attributed to other modules
          // this is the case when contents of one module is nested into contents of another
          if (name != null && !"".equals(name)) {
            subpackages.add(psiPackage);
          }
        }
      }
    }
    return subpackages.toArray(new PsiPackage[subpackages.size()]);
  }

  public static void addPackageAsChild(final Collection<AbstractTreeNode> children,
                                       final PsiPackage aPackage,
                                       Module module,
                                       ViewSettings settings,
                                       final boolean inLibrary) {
    final boolean shouldSkipPackage = settings.isHideEmptyMiddlePackages() && isPackageEmpty(aPackage, module, !settings.isFlattenPackages(), inLibrary);
    final Project project = aPackage.getProject();
    if (!shouldSkipPackage) {
      children.add(new PackageElementNode(project,
                                          new PackageElement(module, aPackage, inLibrary), settings));
    }
    if (settings.isFlattenPackages() || shouldSkipPackage) {
      final PsiPackage[] subpackages = getSubpackages(aPackage, module, project, inLibrary);
      for (PsiPackage subpackage : subpackages) {
        addPackageAsChild(children, subpackage, module, settings, inLibrary);
      }
    }
  }

  public static boolean isPackageEmpty(PsiPackage aPackage,
                                       Module module,
                                       boolean strictlyEmpty,
                                       final boolean inLibrary) {
    final Project project = aPackage.getProject();
    final GlobalSearchScope scopeToShow = getScopeToShow(project, module, inLibrary);
    final PsiDirectory[] dirs = aPackage.getDirectories(scopeToShow);
    for (final PsiDirectory dir : dirs) {
      if (!TreeViewUtil.isEmptyMiddlePackage(dir, strictlyEmpty)) {
        return false;
      }
    }
    return true;
  }

  public static GlobalSearchScope getScopeToShow(final Project project, final Module module, boolean forLibraries) {
    if (module != null) {
      if (forLibraries) {
        return new ModuleLibrariesSearchScope(module);
      }
      else {
        return GlobalSearchScope.moduleScope(module);
      }
    }
    else {
      if (forLibraries) {
        return new ProjectLibrariesSearchScope(project);
      }
      else {
        return GlobalSearchScope.projectScope(project);
      }
    }
  }


  public static boolean isPackageDefault(PsiPackage directoryPackage) {
    final String qName = directoryPackage.getQualifiedName();
    return qName.length() == 0;
  }

  public static Collection<AbstractTreeNode> createPackageViewChildrenOnFiles(final List<VirtualFile> sourceRoots,
                                                                              final Project project,
                                                                              final ViewSettings settings,
                                                                              final Module module,
                                                                              final boolean inLibrary) {
    final PsiManager psiManager = PsiManager.getInstance(project);

    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Set<PsiPackage> topLevelPackages = new HashSet<PsiPackage>();

    for (final VirtualFile root : sourceRoots) {
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory == null) {
        continue;
      }
      final PsiPackage directoryPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (directoryPackage == null || isPackageDefault(directoryPackage)) {
        // add subpackages
        final PsiDirectory[] subdirectories = directory.getSubdirectories();
        for (PsiDirectory subdirectory : subdirectories) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subdirectory);
          if (aPackage != null && !isPackageDefault(aPackage)) {
            topLevelPackages.add(aPackage);
          }
        }
        // add non-dir items
        children.addAll(ProjectViewDirectoryHelper.getInstance(project).getDirectoryChildren(directory, settings, false));
      }
      else {
        topLevelPackages.add(directoryPackage);
      }
    }

    for (final PsiPackage topLevelPackage : topLevelPackages) {
      addPackageAsChild(children, topLevelPackage, module, settings, inLibrary);
    }

    return children;
  }

  public static String getNodeName(final ViewSettings settings, final PsiPackage aPackage, final PsiPackage parentPackageInTree, String defaultShortName,
                            boolean isFQNameShown) {
    final String name;
    if (isFQNameShown) {
      name = settings.isAbbreviatePackageNames() ? TreeViewUtil.calcAbbreviatedPackageFQName(aPackage) : aPackage.getQualifiedName();
    }
    else if (parentPackageInTree != null) {
      PsiPackage parentPackage = aPackage.getParentPackage();
      final StringBuilder buf = new StringBuilder();
      buf.append(aPackage.getName());
      while (parentPackage != null && !parentPackage.equals(parentPackageInTree)) {
        final String parentPackageName = parentPackage.getName();
        if (parentPackageName == null || "".equals(parentPackageName)) {
          break; // reached default package
        }
        buf.insert(0, ".");
        buf.insert(0, parentPackageName);
        parentPackage = parentPackage.getParentPackage();
      }
      name = buf.toString();
    }
    else {
      name = defaultShortName;
    }
    return name;
  }

  private static class ModuleLibrariesSearchScope extends GlobalSearchScope {
    private final Module myModule;

    public ModuleLibrariesSearchScope(final Module module) {
      myModule = module;
    }

    public boolean contains(VirtualFile file) {
      final OrderEntry orderEntry = ModuleRootManager.getInstance(myModule).getFileIndex().getOrderEntryForFile(file);
      return orderEntry instanceof JdkOrderEntry || orderEntry instanceof LibraryOrderEntry;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
      return Comparing.compare(fileIndex.getOrderEntryForFile(file2), fileIndex.getOrderEntryForFile(file1));
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }

  private static class ProjectLibrariesSearchScope extends GlobalSearchScope {
    private final Project myProject;

    public ProjectLibrariesSearchScope(final Project project) {
      myProject = project;
    }

    private static Module[] getModules(final Project project) {
      return ModuleManager.getInstance(project).getModules();
    }

    public boolean contains(VirtualFile file) {
      final Module[] modules = getModules(myProject);
      for (Module module : modules) {
        final OrderEntry orderEntryForFile = ModuleRootManager.getInstance(module).getFileIndex().getOrderEntryForFile(file);
        if (orderEntryForFile instanceof JdkOrderEntry || orderEntryForFile instanceof LibraryOrderEntry) return true;
      }
      return false;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      final Module[] modules = getModules(myProject);
      for (Module module : modules) {
        final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        final OrderEntry orderEntry1 = fileIndex.getOrderEntryForFile(file1);
        if (orderEntry1 != null) {
          final OrderEntry orderEntry2 = fileIndex.getOrderEntryForFile(file2);
          if (orderEntry2 != null) {
            return orderEntry2.compareTo(orderEntry1);
          }
          else {
            return 0;
          }
        }
      }
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }
}
