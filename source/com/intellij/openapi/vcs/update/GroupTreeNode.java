package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
public class GroupTreeNode extends AbstractTreeNode {
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
    String iconName = expanded ? "folderOpen" : "folder";
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

  public void addFilePath(String filePath) {
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
    for (Iterator each = myFilePaths.iterator(); each.hasNext();) {
      files.add(new File((String)each.next()));
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

    for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
      File file = (File)iterator.next();
      FileOrDirectoryTreeNode child = files.contains(file) ?
                               new FileTreeNode(file.getAbsolutePath(), myInvalidAttributes, myProject, parentPath)
                               : (FileOrDirectoryTreeNode)new DirectoryTreeNode(file.getAbsolutePath(), myProject, parentPath);
      parentNode.add(child);
      addFiles(child, groupByPackages.getChildren(file), files, groupByPackages, child.getFilePath());
    }
  }

  private void buildFiles() {
    Collections.sort(myFilePaths, new Comparator<String>(){
      public int compare(String path1, String path2) {
        return path1.compareToIgnoreCase(path2);
      }
    });

    for (Iterator each = myFilePaths.iterator(); each.hasNext();) {
      String filePath = (String)each.next();
      add(new FileTreeNode(filePath, myInvalidAttributes, myProject, null));
    }
  }

  private boolean containsGroups(){
    return myFilePaths.isEmpty();
  }
}
