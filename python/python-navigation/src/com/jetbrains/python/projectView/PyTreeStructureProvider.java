// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectView;

import com.intellij.python.sdk.backend.PyInterpreterLibraryKt;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModelKt;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.evolution.EvoPyProject;
import com.intellij.python.sdk.backend.PythonInterpreterRegistry;
import com.jetbrains.python.codeInsight.typing.PyBundledStubs;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public final class PyTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  @Override
  public @NotNull Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent,
                                                         @NotNull Collection<AbstractTreeNode<?>> children,
                                                         ViewSettings settings) {
    final Project project = parent.getProject();
    final PythonInterpreter interpreter = project != null ? getPythonInterpreter(project, parent) : null;
    if (interpreter != null) {
      final Collection<AbstractTreeNode<?>> newChildren = hideSkeletons(children);
      final PySkeletonsNode skeletonsNode = PySkeletonsNode.create(project, interpreter, settings);
      if (skeletonsNode != null) {
        newChildren.add(skeletonsNode);
      }
      final PyRemoteLibrariesNode remoteLibrariesNode = PyRemoteLibrariesNode.create(project, interpreter, settings);
      if (remoteLibrariesNode != null) {
        newChildren.add(remoteLibrariesNode);
      }
      final PyTypeShedNode typeShedNode = PyTypeShedNode.Companion.create(project, interpreter, settings);
      if (typeShedNode != null) {
        newChildren.add(typeShedNode);
      }
      final PyBundledStubsNode bundledStubsNode = PyBundledStubsNode.Companion.create(project, interpreter, settings);
      if (bundledStubsNode != null) {
        newChildren.add(bundledStubsNode);
      }
      return newChildren;
    }
    if (parent instanceof ExternalLibrariesNode && project != null) {
      return drawInterpreterLibraries(project, children, settings);
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

  /**
   * Draws each interpreter library among [children] as its interpreter, see [PyInterpreterLibraryNode]. A Python SDK is
   * not shown: a module keeps it only for an older build.
   */
  private static @NotNull Collection<AbstractTreeNode<?>> drawInterpreterLibraries(@NotNull Project project,
                                                                                   @NotNull Collection<AbstractTreeNode<?>> children,
                                                                                   ViewSettings settings) {
    List<AbstractTreeNode<?>> newChildren = new ArrayList<>(children.size());
    for (AbstractTreeNode<?> child : children) {
      if (child instanceof NamedLibraryElementNode libraryNode && libraryNode.getValue() != null &&
          PyInterpreterLibraryKt.isIgnoredInterpreterEntry(libraryNode.getValue().getOrderEntry())) {
        continue;
      }
      if (child instanceof NamedLibraryElementNode libraryNode &&
          !(child instanceof PyInterpreterLibraryNode) &&
          libraryNode.getValue() != null &&
          PyInterpreterLibraryKt.isInterpreterLibraryCarrier(libraryNode.getValue().getOrderEntry())) {
        newChildren.add(new PyInterpreterLibraryNode(project, libraryNode.getValue(), settings));
      }
      else {
        newChildren.add(child);
      }
    }
    return newChildren;
  }

  private static @Nullable PythonInterpreter getPythonInterpreter(@NotNull Project project, @NotNull AbstractTreeNode<?> node) {
    if (!(node instanceof NamedLibraryElementNode libraryNode) || libraryNode.getValue() == null) return null;
    final LibraryOrSdkOrderEntry entry = libraryNode.getValue().getOrderEntry();
    if (PyInterpreterLibraryKt.isIgnoredInterpreterEntry(entry)) return null;

    // The node of the carrier that the module names its interpreter by, see isIgnoredInterpreterEntry.
    if (PyInterpreterLibraryKt.isInterpreterLibraryCarrier(entry)) {
      return findInterpreter(entry.getOwnerModule());
    }
    if (!(entry instanceof JdkOrderEntry jdkEntry) || !(jdkEntry.getJdk() instanceof Sdk sdk)) return null;
    if (!PythonSdkUtil.isPythonSdk(sdk, true)) return null;
    return PythonInterpreterRegistry.Companion.getInstance(project).interpreterOfIfReady(sdk);
  }

  /** The interpreter of the Python project that resides on [module], read from the snapshot. */
  private static @Nullable PythonInterpreter findInterpreter(@Nullable Module module) {
    EvoPyProject pyProject = module == null ? null : EvoPyProjectModelKt.findEvoPyProjectIfReady(module);
    return pyProject == null ? null : pyProject.getInterpreter();
  }

  private static @NotNull Collection<AbstractTreeNode<?>> hideSkeletons(@NotNull Collection<AbstractTreeNode<?>> children) {
    List<AbstractTreeNode<?>> newChildren = new ArrayList<>();
    for (AbstractTreeNode child : children) {
      if (child instanceof PsiDirectoryNode) {
        PsiDirectory directory = ((PsiDirectoryNode)child).getValue();
        if (directory == null) {
          continue;
        }
        VirtualFile dir = directory.getVirtualFile();
        if (dir.getFileSystem() instanceof JarFileSystem) {
          dir = ((JarFileSystem)dir.getFileSystem()).getLocalByEntry(dir);
        }
        if (dir == null) {
          continue;
        }
        if (PyTypeShed.INSTANCE.isInside(dir)) {
          continue;
        }
        if (PyBundledStubs.INSTANCE.isInside(dir)) {
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
    ReadAction.runBlocking(() -> containingFileRef.set(element.getContainingFile()));
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
    if (!parents.isEmpty()) {
      return parents.get(parents.size() - 1);
    }
    return element.getContainingFile();
  }
}
