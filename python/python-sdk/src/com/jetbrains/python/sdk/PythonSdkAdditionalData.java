// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PythonSdkAdditionalData implements SdkAdditionalData {
  @NonNls private static final String PATHS_ADDED_BY_USER_ROOT = "PATHS_ADDED_BY_USER_ROOT";
  @NonNls private static final String PATH_ADDED_BY_USER = "PATH_ADDED_BY_USER";
  @NonNls private static final String PATHS_REMOVED_BY_USER_ROOT = "PATHS_REMOVED_BY_USER_ROOT";
  @NonNls private static final String PATH_REMOVED_BY_USER = "PATH_REMOVED_BY_USER";
  @NonNls private static final String ASSOCIATED_PROJECT_PATH = "ASSOCIATED_PROJECT_PATH";

  private final VirtualFilePointerContainer myAddedPaths;
  private final VirtualFilePointerContainer myExcludedPaths;

  private final PythonSdkFlavor myFlavor;
  private String myAssociatedModulePath;

  public PythonSdkAdditionalData(@Nullable PythonSdkFlavor flavor) {
    myFlavor = flavor;
    myAddedPaths = VirtualFilePointerManager.getInstance().createContainer(ApplicationManager.getApplication());
    myExcludedPaths = VirtualFilePointerManager.getInstance().createContainer(ApplicationManager.getApplication());
  }

  protected PythonSdkAdditionalData(@NotNull PythonSdkAdditionalData from) {
    myFlavor = from.getFlavor();
    myAddedPaths = from.myAddedPaths.clone(ApplicationManager.getApplication());
    myExcludedPaths = from.myExcludedPaths.clone(ApplicationManager.getApplication());
    myAssociatedModulePath = from.myAssociatedModulePath;
  }

  @NotNull
  public PythonSdkAdditionalData copy() {
    return new PythonSdkAdditionalData(this);
  }

  public void setAddedPathsFromVirtualFiles(@NotNull Set<VirtualFile> addedPaths) {
    myAddedPaths.killAll();
    for (VirtualFile file : addedPaths) {
      myAddedPaths.add(file);
    }
  }

  public void setExcludedPathsFromVirtualFiles(@NotNull Set<VirtualFile> addedPaths) {
    myExcludedPaths.killAll();
    for (VirtualFile file : addedPaths) {
      myExcludedPaths.add(file);
    }
  }

  public String getAssociatedModulePath() {
    return myAssociatedModulePath;
  }

  public void resetAssociatedModulePath() {
    setAssociatedModulePath(null);
  }

  public void setAssociatedModulePath(@Nullable String associatedModulePath) {
    myAssociatedModulePath = associatedModulePath;
  }

  public void associateWithModule(@NotNull Module module) {
    final String path = BasePySdkExtKt.getBasePath(module);
    if (path != null) {
      associateWithModulePath(path);
    }
  }

  public void associateWithModulePath(@NotNull String modulePath) {
    myAssociatedModulePath = FileUtil.toSystemIndependentName(modulePath);
  }

  public void save(@NotNull final Element rootElement) {
    savePaths(rootElement, myAddedPaths, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER);
    savePaths(rootElement, myExcludedPaths, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER);

    if (myAssociatedModulePath != null) {
      rootElement.setAttribute(ASSOCIATED_PROJECT_PATH, myAssociatedModulePath);
    }
  }

  private static void savePaths(Element rootElement, VirtualFilePointerContainer paths, String root, String element) {
    for (String addedPath : paths.getUrls()) {
      final Element child = new Element(root);
      child.setAttribute(element, addedPath);
      rootElement.addContent(child);
    }
  }

  @Nullable
  public PythonSdkFlavor getFlavor() {
    return myFlavor;
  }

  @NotNull
  public static PythonSdkAdditionalData load(Sdk sdk, @Nullable Element element) {
    final PythonSdkAdditionalData data = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
    data.load(element);
    return data;
  }

  protected void load(@Nullable Element element) {
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_ADDED_BY_USER_ROOT, PATH_ADDED_BY_USER),myAddedPaths);
    collectPaths(JDOMExternalizer.loadStringsList(element, PATHS_REMOVED_BY_USER_ROOT, PATH_REMOVED_BY_USER),myExcludedPaths);
    if (element != null) {
      setAssociatedModulePath(element.getAttributeValue(ASSOCIATED_PROJECT_PATH));
    }
  }

  private static void collectPaths(@NotNull List<String> paths, VirtualFilePointerContainer container) {
    for (String path : paths) {
      if (StringUtil.isEmpty(path)) continue;
      final String protocol = VirtualFileManager.extractProtocol(path);
      final String url = protocol != null ? path : VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path);
      container.add(url);
    }
  }


  public Set<VirtualFile> getAddedPathFiles() {
    return getPathsAsVirtualFiles(myAddedPaths);
  }

  public Set<VirtualFile> getExcludedPathFiles() {
    return getPathsAsVirtualFiles(myExcludedPaths);
  }

  private static Set<VirtualFile> getPathsAsVirtualFiles(VirtualFilePointerContainer paths) {
    Set<VirtualFile> ret = Sets.newHashSet();
    Collections.addAll(ret, paths.getFiles());
    return ret;
  }
}
