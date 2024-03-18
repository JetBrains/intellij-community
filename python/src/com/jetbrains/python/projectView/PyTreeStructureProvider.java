// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public final class PyTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  @NotNull
  @Override
  public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                             @NotNull Collection<AbstractTreeNode<?>> children,
                                             ViewSettings settings) {
    final Project project = parent.getProject();
    final Sdk sdk = getPythonSdk(parent);
    if (sdk != null && project != null) {
      final Collection<AbstractTreeNode<?>> newChildren = hideSkeletons(children);
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
      final PyTypeShedNode typeShedNode = PyTypeShedNode.Companion.create(project, sdk, settings);
      if (typeShedNode != null) {
        newChildren.add(typeShedNode);
      }
      return newChildren;
    }
    if (settings != null && settings.isShowMembers()) {
      List<AbstractTreeNode<?>> newChildren = new ArrayList<>();
      for (AbstractTreeNode child : children) {
        PsiFile f;
        if (child instanceof PsiFileNode && (f = ((PsiFileNode)child).getValue()) instanceof PyFile) {
          newChildren.add(new PyFileNode(project, f, settings));
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
          final SdkTypeId type = sdk == null ? null : sdk.getSdkType();
          if (type instanceof PythonSdkType) {
            return sdk;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Collection<AbstractTreeNode<?>> hideSkeletons(@NotNull Collection<AbstractTreeNode<?>> children) {
    List<AbstractTreeNode<?>> newChildren = new ArrayList<>();
    for (AbstractTreeNode child : children) {
      if (child instanceof PsiDirectoryNode) {
        PsiDirectory directory = ((PsiDirectoryNode)child).getValue();
        if (directory == null) {
          continue;
        }
        VirtualFile dir = directory.getVirtualFile();
        if (dir.equals(PyUserSkeletonsUtil.getUserSkeletonsDirectory())) {
          continue;
        }
        if (dir.getFileSystem() instanceof JarFileSystem) {
          dir = ((JarFileSystem)dir.getFileSystem()).getLocalByEntry(dir);
        }
        if (dir == null) {
          continue;
        }
        if (PyTypeShed.INSTANCE.isInside(dir)) {
          continue;
        }
        VirtualFile dirParent = dir.getParent();

        if (dirParent != null && dirParent.getName().equals(PythonSdkUtil.SKELETON_DIR_NAME)) {
          continue;
        }

        if (dirParent != null && dirParent.getName().equals(PythonSdkUtil.REMOTE_SOURCES_DIR_NAME)) {
          continue;
        }
        if (dirParent != null) {
          VirtualFile grandParent = dirParent.getParent();

          if (grandParent != null && grandParent.getName().equals(PythonSdkUtil.REMOTE_SOURCES_DIR_NAME)) {
            continue;
          }
        }
      }
      newChildren.add(child);
    }
    return newChildren;
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
