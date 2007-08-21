package com.intellij.openapi.vcs.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class GroupTreeNode extends AbstractTreeNode implements Disposable {
  private final String myName;
  private final boolean mySupportsDeletion;
  private final List<String> myFilePaths = new ArrayList<String>();
  private final SimpleTextAttributes myInvalidAttributes;
  private final Project myProject;

  public GroupTreeNode(String name, boolean supportsDeletion, SimpleTextAttributes invalidAttributes,
                       Project project) {
    myName = name;
    mySupportsDeletion = supportsDeletion;
    myInvalidAttributes = invalidAttributes;
    myProject = project;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon(boolean expanded) {
    @NonNls String iconName = expanded ? "folderOpen" : "folder";
    return IconLoader.getIcon("/nodes/" + iconName + ".png");
  }

  public Collection<VirtualFile> getVirtualFiles() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (int i = 0; i < getChildCount(); i++) {
      result.addAll(((AbstractTreeNode)getChildAt(i)).getVirtualFiles());
    }
    return result;
  }

  public Collection<File> getFiles() {
    ArrayList<File> result = new ArrayList<File>();
    for (int i = 0; i < getChildCount(); i++) {
      result.addAll(((AbstractTreeNode)getChildAt(i)).getFiles());
    }
    return result;
  }

  protected int getItemsCount() {
    int result = 0;
    Enumeration children = children();
    while (children.hasMoreElements()) {
      AbstractTreeNode treeNode = (AbstractTreeNode)children.nextElement();
      result += treeNode.getItemsCount();
    }
    return result;
  }

  protected boolean showStatistics() {
    return true;
  }

  public SimpleTextAttributes getAttributes() {
    return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }

  public boolean getSupportsDeletion() {
    return mySupportsDeletion;
  }

  public void addFilePath(@NotNull String filePath) {
    myFilePaths.add(filePath);
  }

  public void rebuild(boolean groupByPackages) {
    if (containsGroups()) {
      rebuildGroups(groupByPackages);
    }
    else
      rebuildFiles(groupByPackages);

  }

  private void rebuildGroups(boolean groupByPackages) {
    for (int i = 0; i < getChildCount(); i++)
      ((GroupTreeNode)getChildAt(i)).rebuild(groupByPackages);
  }

  private void rebuildFiles(boolean groupByPackages) {
    for (int i = getChildCount()-1; i >= 0; i--) {
      final TreeNode node = getChildAt(i);
      if (node instanceof Disposable) {
        Disposer.dispose((Disposable)node);
      }
    }
    removeAllChildren();

    if (groupByPackages) {
      buildPackages();
    }
    else {
      buildFiles();
    }

    setTreeModel(myTreeModel);

    if (myTreeModel != null)
      myTreeModel.nodeStructureChanged(this);
  }

  private void buildPackages() {
    ArrayList<File> files = new ArrayList<File>();
    for (final String myFilePath : myFilePaths) {
      files.add(new File(myFilePath));
    }
    GroupByPackages groupByPackages = new GroupByPackages(files);

    List<File> roots = groupByPackages.getRoots();
    addFiles(this, roots, files, groupByPackages, null);

  }

  private void addFiles(AbstractTreeNode parentNode, List<File> roots,
                        final ArrayList<File> files, GroupByPackages groupByPackages, String parentPath) {
    if (roots == null) return;

    Collections.sort(roots, new Comparator<File>(){
      public int compare(File file, File file1) {
        if (files.contains(file) == files.contains(file1))
          return file.getAbsolutePath().compareToIgnoreCase(file1.getAbsolutePath());
        else if (files.contains(file))
          return 1;
        else
          return -1;
      }
    });

    for (final File root : roots) {
      FileOrDirectoryTreeNode child = files.contains(root)
                                      ? new FileTreeNode(root.getAbsolutePath(), myInvalidAttributes, myProject, parentPath)
                                      : new DirectoryTreeNode(root.getAbsolutePath(), myProject, parentPath);
      Disposer.register(((Disposable) parentNode), child);
      parentNode.add(child);
      addFiles(child, groupByPackages.getChildren(root), files, groupByPackages, child.getFilePath());
    }
  }

  private void buildFiles() {
    Collections.sort(myFilePaths, new Comparator<String>(){
      public int compare(String path1, String path2) {
        return path1.compareToIgnoreCase(path2);
      }
    });

    for (final String filePath : myFilePaths) {
      add(new FileTreeNode(filePath, myInvalidAttributes, myProject, null));
    }
  }

  private boolean containsGroups(){
    return myFilePaths.isEmpty();
  }

  public void dispose() {
  }
}
