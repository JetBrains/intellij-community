// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.projectView;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class PyRemoteLibrariesNode extends PsiDirectoryNode {
  private final @NotNull RemoteSdkProperties myRemoteSdkData;

  private PyRemoteLibrariesNode(@NotNull Project project,
                                @NotNull RemoteSdkProperties sdkAdditionalData,
                                @NotNull PsiDirectory value,
                                ViewSettings viewSettings) {
    super(project, value, viewSettings);

    myRemoteSdkData = sdkAdditionalData;
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    data.setPresentableText(PyBundle.message("python.project.view.remote.libraries"));
    data.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Nullable
  public static PyRemoteLibrariesNode create(@NotNull Project project, @NotNull Sdk sdk, ViewSettings settings) {
    SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof RemoteSdkProperties && sdkAdditionalData instanceof PythonSdkAdditionalData) {
      VirtualFile remoteLibrary = PythonSdkUtil.findAnyRemoteLibrary(sdk);

      if (remoteLibrary != null && remoteLibrary.getFileType() instanceof ArchiveFileType) {
        remoteLibrary = JarFileSystem.getInstance().getLocalByEntry(remoteLibrary);
      }

      if (remoteLibrary != null) {
        final VirtualFile remoteLibraries = remoteLibrary.getParent();

        final PsiDirectory remoteLibrariesDirectory = PsiManager.getInstance(project).findDirectory(remoteLibraries);
        if (remoteLibrariesDirectory != null) {
          return new PyRemoteLibrariesNode(project, (RemoteSdkProperties)sdkAdditionalData, remoteLibrariesDirectory, settings);
        }
      }
    }
    return null;
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    return FluentIterable.from(Lists.newArrayList(getValue().getChildren())).transform((Function<PsiElement, AbstractTreeNode<?>>)input -> {
      if (input instanceof PsiFileSystemItem) {
        String path = ((PsiFileSystemItem)input).getVirtualFile().getPath();
        PsiDirectory dir = input instanceof PsiDirectory ? (PsiDirectory)input : getDirectoryForJar((PsiFile)input);
        if (myRemoteSdkData.getPathMappings().canReplaceLocal(path) && dir != null) {
          return new PyRemoteRootNode(myRemoteSdkData.getPathMappings().convertToRemote(path),
                                      getProject(), dir, getSettings());
        }
      }

      return null;
    }).filter(Predicates.notNull()).toList();
  }

  @Nullable
  private PsiDirectory getDirectoryForJar(PsiFile input) {
    VirtualFile jarRoot = getJarRoot(input);
    if (myProject != null && jarRoot != null) {
      return PsiManager.getInstance(myProject).findDirectory(jarRoot);
    }
    else {
      return null;
    }
  }

  @Nullable
  private static VirtualFile getJarRoot(PsiFile input) {
    final VirtualFile file = input.getVirtualFile();
    if (file == null || !file.isValid() || !(file.getFileType() instanceof ArchiveFileType)) {
      return null;
    }
    return JarFileSystem.getInstance().getJarRootForLocalFile(file);
  }

  public static class PyRemoteRootNode extends PsiDirectoryNode {

    private final String myRemotePath;

    public PyRemoteRootNode(String remotePath, Project project, @NotNull PsiDirectory value, ViewSettings viewSettings) {
      super(project, value, viewSettings);
      myRemotePath = remotePath;
    }

    @Override
    protected void updateImpl(@NotNull PresentationData data) {
      data.setPresentableText(myRemotePath);
      data.setIcon(PlatformIcons.FOLDER_ICON);
    }
  }
}
