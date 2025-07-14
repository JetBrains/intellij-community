// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.namespacePackages;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.FoldersComponentTools;
import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@State(name = "PyNamespacePackagesService")
public final class PyNamespacePackagesService implements PersistentStateComponent<PyNamespacePackagesService> {
  private final List<VirtualFile> myNamespacePackageFolders = new ArrayList<>();
  private final FoldersComponentTools myTools = new FoldersComponentTools(myNamespacePackageFolders);
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

  @Transient
  public @NotNull List<VirtualFile> getNamespacePackageFoldersVirtualFiles() {
    removeInvalidNamespacePackageFolders();
    return Collections.unmodifiableList(myNamespacePackageFolders);
  }

  public void setNamespacePackageFolders(@NotNull List<String> folders) {
    myTools.setFoldersAsStrings(folders);
  }

  @Transient
  public void setNamespacePackageFoldersVirtualFiles(@NotNull List<VirtualFile> folders) {
  myTools.setFoldersAsVirtualFiles(folders);
  }

  public void toggleMarkingAsNamespacePackage(@NotNull VirtualFile directory) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS);
    if (!directory.isDirectory()) return;

    if (canBeMarked(directory)) {
      myNamespacePackageFolders.add(directory);
      PyNamespacePackagesStatisticsCollector.logToggleMarkingAsNamespacePackage(true);
    }
    else if (isMarked(directory)) {
      myNamespacePackageFolders.remove(directory);
      PyNamespacePackagesStatisticsCollector.logToggleMarkingAsNamespacePackage(false);
    }
    else {
      throw new IllegalStateException("Can't toggle namespace package state for: " + directory.getName());
    }

    refreshView();
  }

  public boolean canBeMarked(@NotNull VirtualFile virtualFile) {
    if (myModule == null) return false;
    Project project = myModule.getProject();

    if (PyLanguageFacade.getINSTANCE().getEffectiveLanguageLevel(project, virtualFile).isOlderThan(LanguageLevel.PYTHON34)) return false;
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

  @Override
  public @Nullable PyNamespacePackagesService getState() {
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
    if (!ApplicationManager.getApplication().isWriteIntentLockAcquired()) return;
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
