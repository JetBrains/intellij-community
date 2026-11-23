// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.typing.PyBundledStubs;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
class PythonPathEditor extends SdkPathEditor {
  private final @NotNull PathListModel myPathListModel;

  private final @NotNull List<Runnable> myReloadPathsActionCallbacks = new ArrayList<>();

  PythonPathEditor(@NlsContexts.TabTitle @NotNull String displayName,
                   @NotNull OrderRootType orderRootType,
                   @NotNull FileChooserDescriptor descriptor) {
    super(displayName, orderRootType, descriptor);
    myPathListModel = new PathListModel();
  }

  /**
   * Sets the paths of {@code sys.path} that are source roots of the module instead of roots of the interpreter, see
   * {@code PythonInterpreterDetails.sourceRoots}. The list shows them, so the user can remove one, which excludes it.
   * Call it before {@link #reset(Collection, Set, Set)}.
   */
  void setModuleSourceRoots(@NotNull Collection<VirtualFile> roots) {
    myPathListModel.setModuleSourceRoots(roots);
  }

  /**
   * Shows the class roots of an interpreter, see {@code PythonInterpreter.classRoots}, with the paths that the user
   * added and excluded, see {@code PythonInterpreterDetails.facts}. The editor writes nothing: read the result with
   * {@link #getAddedPaths()} and {@link #getExcludedPaths()}.
   */
  void reset(@NotNull Collection<VirtualFile> classRoots, @NotNull Set<VirtualFile> added, @NotNull Set<VirtualFile> excluded) {
    resetPath(myPathListModel.reset(classRoots, added, excluded));
  }

  /** The paths that the user added in this editor, see {@code PythonInterpreterDetails.writeAddedPaths}. */
  @NotNull Set<VirtualFile> getAddedPaths() {
    return myPathListModel.myAdded;
  }

  /** The paths that the user excluded in this editor, see {@code PythonInterpreterDetails.writeExcludedPaths}. */
  @NotNull Set<VirtualFile> getExcludedPaths() {
    return myPathListModel.myExcluded;
  }

  @Override
  protected VirtualFile[] adjustAddedFileSet(Component component, VirtualFile[] files) {
    for (int i = 0, filesLength = files.length; i < filesLength; i++) {
      if (!files[i].isDirectory() && FileTypeRegistry.getInstance().isFileOfType(files[i], ArchiveFileType.INSTANCE)) {
        files[i] = JarFileSystem.getInstance().getJarRootForLocalFile(files[i]);
      }
    }
    if (myPathListModel.add(Arrays.asList(files))) {
      setModified(true);
    }
    return files;
  }

  @Override
  protected void doRemoveItems(int[] indices, JList<VirtualFile> list) {
    List<Pair<VirtualFile, Integer>> removed = new ArrayList<>();
    for (int i : indices) {
      removed.add(Pair.create(getListModel().get(i), i));
    }
    ListUtil.removeIndices(list, myPathListModel.remove(removed));
    list.updateUI();
    setModified(true);
  }

  @Override
  protected ListCellRenderer<VirtualFile> createListCellRenderer(JBList<VirtualFile> list) {
    return BuilderKt.textListCellRenderer("", value -> {
      String suffix = myPathListModel.getPresentationSuffix(value);
      if (!suffix.isEmpty()) suffix = "  " + suffix;
      return getPresentablePath(value) + suffix;
    });
  }

  @Override
  protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
    toolbarDecorator.addExtraAction(new DumbAwareAction(PyBundle.message("sdk.paths.dialog.reload.paths"), null, AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        fireReloadPathsActionCallbacks();
      }
    });
  }

  public void addReloadPathsActionCallback(@NotNull Runnable e) {
    myReloadPathsActionCallbacks.add(e);
  }

  private void fireReloadPathsActionCallbacks() {
    for (Runnable callback : myReloadPathsActionCallbacks) {
      callback.run();
    }
  }

  protected @NlsSafe String getPresentablePath(VirtualFile value) {
    return value.getPresentableUrl();
  }

  private static class PathListModel {
    private final Set<VirtualFile> myFoundFiles = new HashSet<>();
    private Set<VirtualFile> myAdded = new HashSet<>();
    private Set<VirtualFile> myExcluded = new HashSet<>();
    private final Set<VirtualFile> myModuleSourceRoots = new LinkedHashSet<>();

    public boolean add(@NotNull List<VirtualFile> files) {
      for (VirtualFile file : files) {
        if (!myFoundFiles.contains(file)) {
          if (!myExcluded.remove(file)) { //if it was excluded we only delete exclusion mark
            myAdded.add(file);
          }
          else {
            myFoundFiles.add(file);
            return true;
          }
        }
        else {
          myExcluded.remove(file);
        }
      }
      return false;
    }

    public int @NotNull [] remove(@NotNull List<Pair<VirtualFile, Integer>> files) {
      List<Integer> toRemove = new ArrayList<>();
      for (Pair<VirtualFile, Integer> e : files) {
        if (myAdded.contains(e.first)) {
          toRemove.add(e.second);
          myAdded.remove(e.first);
        }
        else if (myExcluded.contains(e.first)) {
          myExcluded.remove(e.first);
        }
        else {
          myExcluded.add(e.first);
        }
      }
      return ArrayUtil.toIntArray(toRemove);
    }

    public void setAdded(Set<VirtualFile> added) {
      myAdded = Sets.newHashSet(added);
    }

    public void setExcluded(Set<VirtualFile> excluded) {
      myExcluded = Sets.newHashSet(excluded);
    }

    public @Nls @NotNull String getPresentationSuffix(VirtualFile file) {
      if (myAdded.contains(file)) {
        return PyBundle.message("sdk.paths.dialog.added.by.user.suffix");
      }
      if (myExcluded.contains(file)) {
        return PyBundle.message("sdk.paths.dialog.removed.by.user.suffix");
      }
      if (myModuleSourceRoots.contains(file)) {
        return PyBundle.message("sdk.paths.dialog.module.source.root.suffix");
      }
      return "";
    }

    void setModuleSourceRoots(@NotNull Collection<VirtualFile> roots) {
      myModuleSourceRoots.clear();
      myModuleSourceRoots.addAll(roots);
    }

    /**
     * The rows for {@code classRoots}, without the stubs. The class roots hold the added paths, so the list shows the
     * other roots first, then the excluded paths, then the added ones.
     */
    public @NotNull List<VirtualFile> reset(@NotNull Collection<VirtualFile> classRoots,
                                            @NotNull Set<VirtualFile> added,
                                            @NotNull Set<VirtualFile> excluded) {
      List<VirtualFile> measured = new ArrayList<>();
      for (VirtualFile root : classRoots) {
        if (!added.contains(root)) measured.add(root);
      }
      List<VirtualFile> result = filterOutStubs(measured);

      myFoundFiles.clear();
      myFoundFiles.addAll(measured);
      // A path of sys.path that is a source root of the module is no root of the interpreter, so the list adds it.
      for (VirtualFile root : myModuleSourceRoots) {
        if (myFoundFiles.add(root)) result.add(root);
      }

      setAdded(added);
      setExcluded(excluded);
      result.addAll(myExcluded);
      result.addAll(myAdded);
      return result;
    }

    private static @NotNull List<VirtualFile> filterOutStubs(@NotNull List<VirtualFile> list) {
      List<VirtualFile> result = new ArrayList<>();
      for (VirtualFile file : list) {
        if (!isStubPath(file)) {
          result.add(file);
        }
      }
      return result;
    }

    private static boolean isStubPath(@NotNull VirtualFile file) {
      final String path = PythonSdkUtil.getSkeletonsRootPath(PathManager.getSystemPath());
      final VirtualFile skeletonRoot = StandardFileSystems.local().findFileByPath(path);
      if (skeletonRoot != null && file.getPath().startsWith(skeletonRoot.getPath())) {
        return true;
      }
      else if (PyTypeShed.INSTANCE.isInside(file)) {
        return true;
      }
      else if (PyBundledStubs.INSTANCE.isInside(file)) {
        return true;
      }
      else {
        return false;
      }
    }
  }
}