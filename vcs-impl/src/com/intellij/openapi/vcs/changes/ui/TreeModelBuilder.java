package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * @author max
 */
public class TreeModelBuilder {
  @NonNls public static final String ROOT_NODE_VALUE = "root";

  private Project myProject;
  private boolean showFlatten;
  private DefaultTreeModel model;
  private ChangesBrowserNode root;

  public TreeModelBuilder(final Project project, final boolean showFlatten) {
    myProject = project;
    this.showFlatten = showFlatten;
    root = ChangesBrowserNode.create(myProject, ROOT_NODE_VALUE);
    model = new DefaultTreeModel(root);
  }

  public DefaultTreeModel buildModel(final List<Change> changes) {
    final HashMap<FilePath, ChangesBrowserNode> foldersCache = new HashMap<FilePath, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (Change change : changes) {
      insertChangeNode(change, foldersCache, policy, root);
    }

    collapseDirectories(model, root);
    sortNodes();

    return model;
  }

  @Nullable
  private ChangesGroupingPolicy createGroupingPolicy() {
    final ChangesGroupingPolicyFactory factory = ChangesGroupingPolicyFactory.getInstance(myProject);
    if (factory != null) {
      return factory.createGroupingPolicy(model);
    }
    return null;
  }

  public DefaultTreeModel buildModelFromFiles(final List<VirtualFile> files) {
    buildVirtualFiles(files, null);
    collapseDirectories(model, root);
    sortNodes();
    return model;
  }

  public DefaultTreeModel buildModelFromFilePaths(final Collection<FilePath> files) {
    buildFilePaths(files, root);
    collapseDirectories(model, root);
    sortNodes();
    return model;
  }

  public DefaultTreeModel buildModel(final List<? extends ChangeList> changeLists,
                                     final List<VirtualFile> unversionedFiles,
                                     final List<FilePath> locallyDeletedFiles,
                                     final List<VirtualFile> modifiedWithoutEditing,
                                     final MultiMap<String, VirtualFile> switchedFiles,
                                     @Nullable final List<VirtualFile> ignoredFiles, @Nullable final List<VirtualFile> lockedFolders) {

    for (ChangeList list : changeLists) {
      ChangesBrowserNode listNode = ChangesBrowserNode.create(myProject, list);
      model.insertNodeInto(listNode, root, 0);
      final HashMap<FilePath, ChangesBrowserNode> foldersCache = new HashMap<FilePath, ChangesBrowserNode>();
      final ChangesGroupingPolicy policy = createGroupingPolicy();
      for (Change change : list.getChanges()) {
        insertChangeNode(change, foldersCache, policy, listNode);
      }
    }

    if (!modifiedWithoutEditing.isEmpty()) {
      buildVirtualFiles(modifiedWithoutEditing, ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG);
    }
    if (!unversionedFiles.isEmpty()) {
      buildVirtualFiles(unversionedFiles, ChangesBrowserNode.UNVERSIONED_FILES_TAG);
    }
    if (!switchedFiles.isEmpty()) {
      buildSwitchedFiles(switchedFiles);
    }
    if (ignoredFiles != null && !ignoredFiles.isEmpty()) {
      buildVirtualFiles(ignoredFiles, ChangesBrowserNode.IGNORED_FILES_TAG);
    }
    if (lockedFolders != null && !lockedFolders.isEmpty()) {
      buildVirtualFiles(lockedFolders, ChangesBrowserNode.LOCKED_FOLDERS_TAG);
    }

    if (!locallyDeletedFiles.isEmpty()) {
      ChangesBrowserNode locallyDeletedNode = ChangesBrowserNode.create(myProject, VcsBundle.message("changes.nodetitle.locally.deleted.files"));
      model.insertNodeInto(locallyDeletedNode, root, root.getChildCount());
      buildFilePaths(locallyDeletedFiles, locallyDeletedNode);
    }

    collapseDirectories(model, root);
    sortNodes();

    return model;
  }

  private void buildVirtualFiles(final List<VirtualFile> files, @Nullable final Object tag) {
    ChangesBrowserNode baseNode;
    if (tag != null) {
      baseNode = ChangesBrowserNode.create(myProject, tag);
      model.insertNodeInto(baseNode, root, root.getChildCount());
    }
    else {
      baseNode = root;
    }
    final HashMap<FilePath, ChangesBrowserNode> foldersCache = new HashMap<FilePath, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (VirtualFile file : files) {
      insertChangeNode(file, foldersCache, policy, baseNode);
    }
  }

  private void buildFilePaths(final Collection<FilePath> filePaths, final ChangesBrowserNode baseNode) {
    final HashMap<FilePath, ChangesBrowserNode> foldersCache = new HashMap<FilePath, ChangesBrowserNode>();
    final ChangesGroupingPolicy policy = createGroupingPolicy();
    for (FilePath file : filePaths) {
      assert file != null;
      ChangesBrowserNode oldNode = foldersCache.get(file);
      if (oldNode == null) {
        final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, file);
        model.insertNodeInto(node, getParentNodeFor(node, foldersCache, policy, baseNode), 0);
        foldersCache.put(file, node);
      }
    }
  }

  private void buildSwitchedFiles(final MultiMap<String, VirtualFile> switchedFiles) {
    ChangesBrowserNode baseNode = ChangesBrowserNode.create(myProject, ChangesBrowserNode.SWITCHED_FILES_TAG);
    model.insertNodeInto(baseNode, root, root.getChildCount());
    for(String branchName: switchedFiles.keySet()) {
      final List<VirtualFile> switchedFileList = switchedFiles.get(branchName);
      if (switchedFileList.size() > 0) {
        ChangesBrowserNode branchNode = ChangesBrowserNode.create(myProject, branchName);
        model.insertNodeInto(branchNode, baseNode, baseNode.getChildCount());

        final HashMap<FilePath, ChangesBrowserNode> foldersCache = new HashMap<FilePath, ChangesBrowserNode>();
        final ChangesGroupingPolicy policy = createGroupingPolicy();
        for (VirtualFile file : switchedFileList) {
          insertChangeNode(file, foldersCache, policy, branchNode);
        }
      }
    }
  }

  private void insertChangeNode(final Object change, final HashMap<FilePath, ChangesBrowserNode> foldersCache,
                                final ChangesGroupingPolicy policy,
                                final ChangesBrowserNode listNode) {
    final FilePath nodePath = getPathForObject(change);
    ChangesBrowserNode oldNode = foldersCache.get(nodePath);
    if (oldNode != null) {
      ChangesBrowserNode node = ChangesBrowserNode.create(myProject, change);
      for(int i=oldNode.getChildCount()-1; i >= 0; i--) {
        MutableTreeNode child = (MutableTreeNode) model.getChild(oldNode, i);
        model.removeNodeFromParent(child);
        model.insertNodeInto(child, node, model.getChildCount(node));
      }
      final MutableTreeNode parent = (MutableTreeNode)oldNode.getParent();
      int index = model.getIndexOfChild(parent, oldNode);
      model.removeNodeFromParent(oldNode);
      model.insertNodeInto(node, parent, index);
      foldersCache.put(nodePath, node);
    }
    else {
      final ChangesBrowserNode node = ChangesBrowserNode.create(myProject, change);
      ChangesBrowserNode parentNode = getParentNodeFor(node, foldersCache, policy, listNode);
      model.insertNodeInto(node, parentNode, model.getChildCount(parentNode));
      foldersCache.put(nodePath, node);
    }
  }

  private void sortNodes() {
    TreeUtil.sort(model, new Comparator() {
      public int compare(final Object n1, final Object n2) {
        final ChangesBrowserNode node1 = (ChangesBrowserNode)n1;
        final ChangesBrowserNode node2 = (ChangesBrowserNode)n2;

        final int classdiff = node1.getSortWeight() - node2.getSortWeight();
        if (classdiff != 0) return classdiff;

        return node1.compareUserObjects(node2.getUserObject());
      }
   });

    model.nodeStructureChanged((TreeNode)model.getRoot());
  }

  private static void collapseDirectories(DefaultTreeModel model, ChangesBrowserNode node) {
    if (node.getUserObject() instanceof FilePath && node.getChildCount() == 1) {
      final ChangesBrowserNode child = (ChangesBrowserNode)node.getChildAt(0);
      if (child.getUserObject() instanceof FilePath && !child.isLeaf()) {
        ChangesBrowserNode parent = (ChangesBrowserNode)node.getParent();
        final int idx = parent.getIndex(node);
        model.removeNodeFromParent(node);
        model.removeNodeFromParent(child);
        model.insertNodeInto(child, parent, idx);
        collapseDirectories(model, parent);
      }
    }
    else {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        ChangesBrowserNode child = (ChangesBrowserNode)children.nextElement();
        collapseDirectories(model, child);
      }
    }
  }

  public static FilePath getPathForObject(Object o) {
    if (o instanceof Change) {
      return ChangesUtil.getFilePath((Change)o);
    }
    else if (o instanceof VirtualFile) {
      return new FilePathImpl((VirtualFile) o);
    }
    else if (o instanceof FilePath) {
      return (FilePath)o;
    }

    return null;
  }

  private ChangesBrowserNode getParentNodeFor(ChangesBrowserNode node,
                                Map<FilePath, ChangesBrowserNode> folderNodesCache,
                                @Nullable ChangesGroupingPolicy policy,
                                ChangesBrowserNode rootNode) {
    if (showFlatten) {
      return rootNode;
    }

    final FilePath path = getPathForObject(node.getUserObject());

    if (policy != null) {
      ChangesBrowserNode nodeFromPolicy = policy.getParentNodeFor(node, rootNode);
      if (nodeFromPolicy != null) {
        return nodeFromPolicy;
      }
    }

    FilePath parentPath = path.getParentPath();
    if (parentPath == null) {
      return rootNode;
    }

    ChangesBrowserNode parentNode = folderNodesCache.get(parentPath);
    if (parentNode == null) {
      parentNode = ChangesBrowserNode.create(myProject, parentPath);
      ChangesBrowserNode grandPa = getParentNodeFor(parentNode, folderNodesCache, policy, rootNode);
      model.insertNodeInto(parentNode, grandPa, grandPa.getChildCount());
      folderNodesCache.put(parentPath, parentNode);
    }

    return parentNode;
  }
}
