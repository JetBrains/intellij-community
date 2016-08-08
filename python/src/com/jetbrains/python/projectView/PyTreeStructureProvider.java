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

import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    final Project project = parent.getProject();
    final Sdk sdk = getPythonSdk(parent);
    if (sdk != null && project != null) {
      final Collection<AbstractTreeNode> newChildren = hideSkeletons(children);
      final PySkeletonsNode skeletonsNode = PySkeletonsNode.create(project, sdk, settings);
      if (skeletonsNode != null) {
        newChildren.add(skeletonsNode);
      }
      final PyUserSkeletonsNode userSkeletonsNode = PyUserSkeletonsNode.create(project, settings);
      if (userSkeletonsNode != null) {
        newChildren.add(userSkeletonsNode);
      }
      final PyRemoteLibrariesNode remoteLibrariesNode = PyRemoteLibrariesNode.create(project, sdk, settings);
      if (remoteLibrariesNode != null) {
        newChildren.add(remoteLibrariesNode);
      }
      return newChildren;
    }
    if (settings.isShowMembers()) {
      List<AbstractTreeNode> newChildren = new ArrayList<>();
      for (AbstractTreeNode child : children) {
        if (child instanceof PsiFileNode && ((PsiFileNode)child).getValue() instanceof PyFile) {
          newChildren.add(new PyFileNode(project, ((PsiFileNode)child).getValue(), settings));
        }
        else {
          newChildren.add(child);
        }
      }
      return newChildren;
    }
    return children;
  }

  @Nullable
  private static Sdk getPythonSdk(@NotNull AbstractTreeNode node) {
    if (node instanceof NamedLibraryElementNode) {
      final NamedLibraryElement value = ((NamedLibraryElementNode)node).getValue();
      if (value != null) {
        final LibraryOrSdkOrderEntry entry = value.getOrderEntry();
        if (entry instanceof JdkOrderEntry) {
          final Sdk sdk = ((JdkOrderEntry)entry).getJdk();
          final SdkTypeId type = sdk.getSdkType();
          if (type instanceof PythonSdkType) {
            return sdk;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Collection<AbstractTreeNode> hideSkeletons(@NotNull Collection<AbstractTreeNode> children) {
    List<AbstractTreeNode> newChildren = new ArrayList<>();
    for (AbstractTreeNode child : children) {
      if (child instanceof PsiDirectoryNode) {
        PsiDirectory directory = ((PsiDirectoryNode)child).getValue();
        if (directory.getVirtualFile().equals(PyUserSkeletonsUtil.getUserSkeletonsDirectory())) {
          continue;
        }
        VirtualFile dir = directory.getVirtualFile();
        if (dir.getFileSystem() instanceof JarFileSystem) {
          dir = ((JarFileSystem)directory.getVirtualFile().getFileSystem()).getLocalVirtualFileFor(directory.getVirtualFile());
        }
        if (dir == null) {
          continue;
        }
        VirtualFile dirParent = dir.getParent();

        if (dirParent != null && dirParent.getName().equals(PythonSdkType.SKELETON_DIR_NAME)) {
          continue;
        }

        if (dirParent != null && dirParent.getName().equals(PythonSdkType.REMOTE_SOURCES_DIR_NAME)) {
          continue;
        }
        if (dirParent != null) {
          VirtualFile grandParent = dirParent.getParent();

          if (grandParent != null && grandParent.getName().equals(PythonSdkType.REMOTE_SOURCES_DIR_NAME)) {
            continue;
          }
        }
      }
      newChildren.add(child);
    }
    return newChildren;
  }

  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  @Override
  public PsiElement getTopLevelElement(PsiElement element) {
    PyPsiUtils.assertValid(element);
    final Ref<PsiFile> containingFileRef = Ref.create();
    ApplicationManager.getApplication().runReadAction(() -> containingFileRef.set(element.getContainingFile()));
    final PsiFile containingFile = containingFileRef.get();
    if (!(containingFile instanceof PyFile)) {
      return null;
    }
    List<PsiElement> parents = new ArrayList<>();
    PyDocStringOwner container = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
    while (container != null) {
      if (container instanceof PyFile) {
        break;
      }
      parents.add(0, container);
      container = PsiTreeUtil.getParentOfType(container, PyDocStringOwner.class);
    }
    for (PsiElement parent : parents) {
      if (parent instanceof PyFunction) {
        return parent;     // we don't display any nodes under functions
      }
    }
    if (parents.size() > 0) {
      return parents.get(parents.size() - 1);
    }
    return element.getContainingFile();
  }
}
