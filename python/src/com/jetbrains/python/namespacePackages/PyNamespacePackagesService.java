// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.namespacePackages;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(name = "PyNamespacePackagesService")
public class PyNamespacePackagesService implements PersistentStateComponent<PyNamespacePackagesService> {
  private final List<VirtualFile> myNamespacePackageFolders = new ArrayList<>();
  private final Module myModule;

  public PyNamespacePackagesService() {
    myModule = null;
  }

  public PyNamespacePackagesService(@Nullable Module module) {
    myModule = module;
  }

  public static @NotNull PyNamespacePackagesService getInstance(@NotNull Module module) {
    return module.getService(PyNamespacePackagesService.class);
  }

  public @NotNull List<String> getNamespacePackageFolders() {
    removeInvalidNamespacePackageFolders();
    return Collections.unmodifiableList(ContainerUtil.map(myNamespacePackageFolders, it -> it.getPath()));
  }

  @Transient
  public @NotNull List<VirtualFile> getNamespacePackageFoldersVirtualFiles() {
    removeInvalidNamespacePackageFolders();
    return Collections.unmodifiableList(myNamespacePackageFolders);
  }

  public void setNamespacePackageFolders(@NotNull List<String> folders) {
    myNamespacePackageFolders.clear();
    for (String path: folders) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      if (virtualFile != null) {
        myNamespacePackageFolders.add(virtualFile);
      }
    }
  }

  @Transient
  public void setNamespacePackageFoldersVirtualFiles(@NotNull List<VirtualFile> folders) {
    myNamespacePackageFolders.clear();
    myNamespacePackageFolders.addAll(folders);
  }

  public void toggleMarkingAsNamespacePackage(@NotNull VirtualFile directory) {
    if (!directory.isDirectory()) return;

    if (canBeMarked(directory)) {
      myNamespacePackageFolders.add(directory);
      PyNamespacePackagesStatisticsCollector.Companion.logToggleMarkingAsNamespacePackage(true);
    }
    else if (isMarked(directory)) {
      myNamespacePackageFolders.remove(directory);
      PyNamespacePackagesStatisticsCollector.Companion.logToggleMarkingAsNamespacePackage(false);
    }
    else {
      throw new IllegalStateException("Can't toggle namespace package state for: " + directory.getName());
    }

    refreshView();
  }

  public boolean canBeMarked(@NotNull VirtualFile virtualFile) {
    if (myModule == null) return false;
    Project project = myModule.getProject();

    if (PythonLanguageLevelPusher.getLanguageLevelForVirtualFile(project, virtualFile).isOlderThan(LanguageLevel.PYTHON34)) return false;
    if (PyUtil.isRoot(virtualFile, project)) return false;
    if (!isInProject(virtualFile, project)) return false;
    PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
    if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) return false;

    VirtualFile curDir = virtualFile;
    while (curDir != null) {
      if (!curDir.isDirectory()) return false;
      if (myNamespacePackageFolders.contains(curDir)) return false;
      if (PyUtil.isRoot(curDir, project)) break;
      psiDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(curDir);
      if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) break;
      curDir = curDir.getParent();
    }

    return true;
  }

  @Nullable
  @Override
  public PyNamespacePackagesService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyNamespacePackagesService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isMarked(@NotNull VirtualFile dir) {
    return myNamespacePackageFolders.contains(dir);
  }

  public boolean isNamespacePackage(VirtualFile directory) {
    if (myModule != null) {
      PsiDirectory psiDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(directory);
      if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) return false;
    }
    VirtualFile curDir = directory;
    while (curDir != null) {
      if (isMarked(curDir)) return true;
      if (myModule != null) {
        if (PyUtil.isRoot(directory, myModule.getProject())) break;
        PsiDirectory psiDirectory = PsiManager.getInstance(myModule.getProject()).findDirectory(curDir);
        if (psiDirectory != null && PyUtil.isOrdinaryPackage(psiDirectory)) break;
      }
      curDir = curDir.getParent();
    }
    return false;
  }

  public static boolean isEnabled() {
    return Registry.is("python.explicit.namespace.packages");
  }

  private void removeInvalidNamespacePackageFolders() {
    myNamespacePackageFolders.removeIf(it -> it == null || !it.isValid());
  }

  private static boolean isInProject(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiDirectory psiDirectory = psiManager.findDirectory(virtualFile);
    if (psiDirectory == null) return false;
    if (!psiManager.isInProject(psiDirectory)) return false;
    return true;
  }

  private void refreshView() {
    if (!ApplicationManager.getApplication().isWriteThread()) return;
    if (myModule == null) return;
    Project project = myModule.getProject();
    ProjectView.getInstance(project).refresh();
    PsiManager.getInstance(project).dropPsiCaches();
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @TestOnly
  public void resetAllNamespacePackages() {
    myNamespacePackageFolders.clear();
  }
}
