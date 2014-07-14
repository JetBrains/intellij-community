/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.projectView;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.sdk.PySdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author traff
 */
public class PyRemoteLibrariesNode extends PsiDirectoryNode {
  private final Sdk mySdk;
  private final PyRemoteSdkAdditionalDataBase myRemoteSdkData;

  private PyRemoteLibrariesNode(Sdk sdk, Project project, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
    mySdk = sdk;
    assert mySdk.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataBase;

    myRemoteSdkData = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
  }

  @Override
  protected void updateImpl(PresentationData data) {
    data.setPresentableText("Remote Libraries");
    data.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Nullable
  public static PyRemoteLibrariesNode create(@NotNull Project project, @NotNull Sdk sdk, ViewSettings settings) {
    final VirtualFile remoteLibrary = PySdkUtil.findAnyRemoteLibrary(sdk);
    if (remoteLibrary != null) {
      final VirtualFile remoteLibraries = remoteLibrary.getParent();
      final PsiDirectory remoteLibrariesDirectory = PsiManager.getInstance(project).findDirectory(remoteLibraries);
      return new PyRemoteLibrariesNode(sdk, project, remoteLibrariesDirectory, settings);
    }
    return null;
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {

    return FluentIterable.from(Lists.newArrayList(getValue().getChildren())).transform(new Function<PsiElement, AbstractTreeNode>() {
      @Override
      public AbstractTreeNode apply(PsiElement input) {
        if (input instanceof PsiDirectory) {
          PsiDirectory directory = (PsiDirectory)input;
          if (myRemoteSdkData.getPathMappings().canReplaceLocal((directory.getVirtualFile().getPath()))) {
            return new PyRemoteRootNode(myRemoteSdkData.getPathMappings().convertToRemote(directory.getVirtualFile().getPath()),
                                        getProject(), directory, getSettings());
          }
        }

        return null;
      }
    }).filter(Predicates.notNull()).toList();
  }

  public static class PyRemoteRootNode extends PsiDirectoryNode {

    private String myRemotePath;

    public PyRemoteRootNode(String remotePath, Project project, PsiDirectory value, ViewSettings viewSettings) {
      super(project, value, viewSettings);
      myRemotePath = remotePath;
    }

    @Override
    protected void updateImpl(PresentationData data) {
      data.setPresentableText(myRemotePath);
      data.setIcon(PlatformIcons.FOLDER_ICON);
    }
  }
}
