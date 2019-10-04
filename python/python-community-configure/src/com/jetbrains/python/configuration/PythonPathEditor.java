// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ListUtil;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author traff
 */
public class PythonPathEditor extends SdkPathEditor {
  private final PathListModel myPathListModel;

  public PythonPathEditor(final String displayName,
                          @NotNull OrderRootType orderRootType,
                          final FileChooserDescriptor descriptor) {
    super(displayName, orderRootType, descriptor);
    myPathListModel = new PathListModel(orderRootType, getListModel());
  }

  @Override
  public void reset(@Nullable SdkModificator modificator) {
    if (modificator != null) {
      List<VirtualFile> list = Lists.newArrayList(modificator.getRoots(getOrderRootType()));
      resetPath(myPathListModel.reset(list, modificator));
    }
    else {
      setEnabled(false);
    }
  }

  public void reload(@Nullable SdkModificator sdkModificator) {
    if (sdkModificator != null) {
      List<VirtualFile> list = Lists.newArrayList(sdkModificator.getRoots(getOrderRootType()));
      resetPath(myPathListModel.reload(list));
      setModified(true);
    }
    else {
      setEnabled(false);
    }
  }

  @Override
  public void apply(SdkModificator sdkModificator) {
    sdkModificator.removeRoots(getOrderRootType());
    // add all items
    for (int i = 0; i < myPathListModel.getRowCount(); i++) {
      VirtualFile path = myPathListModel.getValueAt(i);
      if (!myPathListModel.isExcluded(path)) {
        sdkModificator.addRoot(path, getOrderRootType());
      }
    }
    setModified(false);
    myPathListModel.apply(sdkModificator);
  }

  @Override
  protected VirtualFile[] adjustAddedFileSet(Component component, VirtualFile[] files) {
    for (int i = 0, filesLength = files.length; i < filesLength; i++) {
      if (!files[i].isDirectory() && FileTypeRegistry.getInstance().isFileOfType(files[i], ArchiveFileType.INSTANCE)) {
        files[i] = JarFileSystem.getInstance().getJarRootForLocalFile(files[i]);
      }
    }
    if (myPathListModel.add(Lists.newArrayList(files))) {
      setModified(true);
    }
    return files;
  }

  @Override
  protected void doRemoveItems(int[] idxs, JList list) {
    List<Pair<VirtualFile, Integer>> removed = Lists.newArrayList();
    for (int i : idxs) {
      removed.add(Pair.create(getListModel().get(i), i));
    }
    ListUtil.removeIndices(list, myPathListModel.remove(removed));
    list.updateUI();
    setModified(true);
  }

  @Override
  protected ListCellRenderer<VirtualFile> createListCellRenderer(JBList list) {
    return SimpleListCellRenderer.create("", value -> {
      String suffix = myPathListModel.getPresentationSuffix(value);
      if (suffix.length() > 0) {
        suffix = "  " + suffix;
      }
      return getPresentablePath(value) + suffix;
    });
  }

  @Override
  protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
    AnActionButton reloadButton = new AnActionButton(PyBundle.message("sdk.paths.dialog.reload.paths"), AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        onReloadButtonClicked();
      }
    };
    toolbarDecorator.addExtraAction(reloadButton);
  }

  protected void onReloadButtonClicked() {
  }

  private static class PathListModel {
    private Set<VirtualFile> myAdded = Sets.newHashSet();
    private Set<VirtualFile> myExcluded = Sets.newHashSet();
    private final Set<VirtualFile> myFoundFiles = Sets.newHashSet();
    private final List<VirtualFile> myFilteredOut = Lists.newArrayList();
    private final DefaultListModel myListModel;
    private final OrderRootType myOrderRootType;
    private final Set<VirtualFile> myUserAddedToRemove = Sets.newHashSet();

    PathListModel(OrderRootType orderRootType, DefaultListModel listModel) {
      myOrderRootType = orderRootType;
      myListModel = listModel;
    }

    private int getRowCount() {
      return myListModel.getSize();
    }

    private VirtualFile getValueAt(int row) {
      return (VirtualFile)myListModel.get(row);
    }

    public boolean add(List<VirtualFile> files) {
      for (VirtualFile file : files) {
        if (!myFoundFiles.contains(file)) {
          if (!myExcluded.remove(file)) { //if it was excluded we only delete exclusion mark
            myAdded.add(file);
            myUserAddedToRemove.remove(file);
          }
          else {
            myFoundFiles.add(file);
            return true;
          }
        } else {
          myExcluded.remove(file);
        }
      }
      return false;
    }

    public int[] remove(List<Pair<VirtualFile, Integer>> files) {
      List<Integer> toRemove = Lists.newArrayList();
      for (Pair<VirtualFile, Integer> e : files) {
        if (myAdded.contains(e.first)) {
          toRemove.add(e.second);
          myAdded.remove(e.first);
          myUserAddedToRemove.add(e.first);
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

    public void apply(SdkModificator sdkModificator) {
      sdkModificator.setSdkAdditionalData(collectSdkAdditionalData(sdkModificator));
      addFilteredOutRoots(sdkModificator);
    }

    private void addFilteredOutRoots(SdkModificator sdkModificator) {
      for (VirtualFile file : myFilteredOut) {
        sdkModificator.addRoot(file, myOrderRootType);
      }
    }

    private SdkAdditionalData collectSdkAdditionalData(SdkModificator sdkModificator) {
      PythonSdkAdditionalData data = (PythonSdkAdditionalData)sdkModificator.getSdkAdditionalData();
      if (data == null) {
        data = new PythonSdkAdditionalData(null);
      }
      data.setAddedPathsFromVirtualFiles(myAdded);
      data.setExcludedPathsFromVirtualFiles(myExcluded);
      return data;
    }

    public void setAdded(Set<VirtualFile> added) {
      myAdded = Sets.newHashSet(added);
    }

    public void setExcluded(Set<VirtualFile> excluded) {
      myExcluded = Sets.newHashSet(excluded);
    }

    public String getPresentationSuffix(VirtualFile file) {
      if (myAdded.contains(file)) {
        return PyBundle.message("sdk.paths.dialog.added.by.user.suffix");
      }
      if (myExcluded.contains(file)) {
        return PyBundle.message("sdk.paths.dialog.removed.by.user.suffix");
      }
      return "";
    }

    public List<VirtualFile> reload(List<VirtualFile> list) {
      myFoundFiles.clear();
      myFoundFiles.addAll(list);
      List<VirtualFile> result = filterOutStubs(list, myFilteredOut);
      result.removeAll(myUserAddedToRemove);
      result.addAll(myAdded);

      return result;
    }

    public List<VirtualFile> reset(List<VirtualFile> list, SdkModificator modificator) {
      myFilteredOut.clear();
      List<VirtualFile> result = filterOutStubs(list, myFilteredOut);

      myFoundFiles.clear();
      myFoundFiles.addAll(list);
      myUserAddedToRemove.clear();

      if (modificator.getSdkAdditionalData() instanceof PythonSdkAdditionalData) {
        PythonSdkAdditionalData data = (PythonSdkAdditionalData)modificator.getSdkAdditionalData();
        setAdded(data.getAddedPathFiles());
        setExcluded(data.getExcludedPathFiles());
        result.addAll(myExcluded);
        result.addAll(myAdded);
      }
      else if (modificator.getSdkAdditionalData() == null) {
        myAdded.clear();
        myExcluded.clear();
      }
      return result;
    }

    private static List<VirtualFile> filterOutStubs(List<VirtualFile> list, List<VirtualFile> filteredOut) {
      List<VirtualFile> result = Lists.newArrayList();
      filteredOut.clear();
      for (VirtualFile file : list) {
        if (!isStubPath(file)) {
          result.add(file);
        }
        else {
          filteredOut.add(file);
        }
      }
      return result;
    }

    private static boolean isStubPath(@NotNull VirtualFile file) {
      final String path = PythonSdkType.getSkeletonsRootPath(PathManager.getSystemPath());
      final VirtualFile skeletonRoot = LocalFileSystem.getInstance().findFileByPath(path);
      if (skeletonRoot != null && file.getPath().startsWith(skeletonRoot.getPath())) {
        return true;
      }
      else if (file.equals(PyUserSkeletonsUtil.getUserSkeletonsDirectory())) {
        return true;
      }
      else if (PyTypeShed.INSTANCE.isInside(file)) {
        return true;
      }
      else {
        return false;
      }
    }

    public boolean isExcluded(VirtualFile path) {
      return myExcluded.contains(path);
    }
  }

  protected String getPresentablePath(VirtualFile value) {
    return value.getPresentableUrl();
  }
}
