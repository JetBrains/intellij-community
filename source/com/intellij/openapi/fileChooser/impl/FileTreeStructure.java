/**
 * @author Yura Cangea
 */
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileTreeStructure");
  private final RootFileElement myRootElement;
  private final FileChooserDescriptor myChooserDescriptor;
  private boolean myShownHiddens;
  private final Project myProject;

  public FileTreeStructure(Project project, FileChooserDescriptor chooserDescriptor) {
    myProject = project;
    List<VirtualFile> roots = chooserDescriptor.getRoots();
    final VirtualFile[] rootFiles = roots.toArray(new VirtualFile[roots.size()]);
    VirtualFile rootFile = rootFiles.length == 1 ? rootFiles[0] : null;
    myRootElement = new RootFileElement(rootFiles, rootFile != null? rootFile.getPresentableUrl() : chooserDescriptor.getTitle(), chooserDescriptor.isShowFileSystemRoots());
    myChooserDescriptor = chooserDescriptor;

    String value = PropertiesComponent.getInstance().getValue("FileChooser.showHiddens");
    myShownHiddens = Boolean.valueOf(value).booleanValue();
  }

  public final boolean areHiddensShown() {
    return myShownHiddens;
  }

  public final void showHiddens(final boolean showHiddens) {
    myShownHiddens = showHiddens;
  }

  public final Object getRootElement() {
    return myRootElement;
  }

  public Object[] getChildElements(Object element) {
    if (element instanceof FileElement) {
      return getFileChildren((FileElement) element);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public final FileChooserDescriptor getChooserDescriptor() {
    return myChooserDescriptor;
  }

  private Object[] getFileChildren(FileElement element) {
    if (element.getFile() == null) {
      if (element == myRootElement) {
        return myRootElement.getChildren();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    VirtualFile[] children = null;

    if (element.isArchive() && myChooserDescriptor.isChooseJarContents()) {
      VirtualFile file = element.getFile();
      String path = file.getPath();
      if (!(file.getFileSystem() instanceof JarFileSystem)) {
        file = JarFileSystem.getInstance().findFileByPath(path + JarFileSystem.JAR_SEPARATOR);
      }
      if (file != null) children = file.getChildren();
    }
    else {
      children = element.getFile().getChildren();
    }

    if (children == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    HashSet<FileElement> childrenSet = new HashSet<FileElement>();
    for (VirtualFile child : children) {
      if (myChooserDescriptor.isFileVisible(child, myShownHiddens)) {
        childrenSet.add(new FileElement(child, child.getName()));
      }
    }
    return childrenSet.toArray(new Object[childrenSet.size()]);
  }


  @Nullable
  public Object getParentElement(Object element) {
    if (element instanceof FileElement) {
      VirtualFile file = ((FileElement) element).getFile();
      if (file == null) return null;
      VirtualFile parent = file.getParent();
      if (parent != null && parent.getFileSystem() instanceof JarFileSystem && parent.getParent() == null) {
        // parent of jar contents should be local jar file
        String localPath = parent.getPath().substring(0,
                                                      parent.getPath().length() - JarFileSystem.JAR_SEPARATOR.length());
        parent = LocalFileSystem.getInstance().findFileByPath(localPath);
      }
      if (parent == null) {
        return null;
      }
      return new FileElement(parent, parent.getName());
    }
    return null;
  }

  public final void commit() {
  }

  public final boolean hasSomethingToCommit() {
    return false;
  }

  public final void dispose() {
    PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", Boolean.toString(myShownHiddens));
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    LOG.assertTrue(element instanceof FileElement, element.getClass().getName());
    VirtualFile file = ((FileElement)element).getFile();
    Icon openIcon = file == null ? null : myChooserDescriptor.getOpenIcon(file);
    Icon closedIcon = file == null ? null : myChooserDescriptor.getClosedIcon(file);
    String name = file == null ? null : myChooserDescriptor.getName(file);
    String comment = file == null ? null : myChooserDescriptor.getComment(file);

    return new FileNodeDescriptor(myProject, (FileElement)element, parentDescriptor, openIcon, closedIcon, name, comment);
  }
}
