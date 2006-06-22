package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.impl.FileComparator;
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder;
import com.intellij.openapi.fileChooser.impl.FileTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ConvertingIterator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;

public class FileSystemTreeImpl implements FileSystemTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileSystemTreeImpl");

  private final Tree myTree;
  private final FileTreeStructure myTreeStructure;
  private final AbstractTreeBuilder myTreeBuilder;
  private final Project myProject;
  private final ArrayList<Runnable> myOkActions = new ArrayList<Runnable>(2);
  private final FileChooserDescriptor myDescriptor;

  public FileSystemTreeImpl(Project project, FileChooserDescriptor descriptor) {
    this(project, descriptor, new Tree());
    myTree.setRootVisible(descriptor.isTreeRootVisible());
    myTree.setShowsRootHandles(true);
  }

  public FileSystemTreeImpl(Project project, FileChooserDescriptor descriptor, Tree tree) {
    myProject = project;
    myTreeStructure = new FileTreeStructure(project, descriptor);
    myDescriptor = descriptor;
    myTree = tree;
    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree.setModel(treeModel);
    myTreeBuilder = createTreeBuilder(myTree, treeModel, myTreeStructure, FileComparator.getInstance(), descriptor);

    new TreeSpeedSearch(myTree);
    myTree.setLineStyleAngled();
    myTree.expandPath(new TreePath(treeModel.getRoot()));
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);

    myTree.getSelectionModel().setSelectionMode(
        myTreeStructure.getChooserDescriptor().getChooseMultiple() ?
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION :
        TreeSelectionModel.SINGLE_TREE_SELECTION
    );
    addTreeExpansionListener();
    registerTreeActions();
  }

  protected AbstractTreeBuilder createTreeBuilder(final JTree tree, DefaultTreeModel treeModel, final AbstractTreeStructure treeStructure, final Comparator<NodeDescriptor> comparator, FileChooserDescriptor descriptor) {
    return new FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor);
  }

  private void registerTreeActions() {
    myTree.registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            performEnterAction(true);
          }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED
    );
    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) performEnterAction(false);
      }
    });
  }

  private void performEnterAction(boolean toggleNodeState) {
    TreePath path = myTree.getSelectionPath();
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null && node.isLeaf()) {
        fireOkAction();
      }
      else if (toggleNodeState) {
        if (myTree.isExpanded(path)) {
          myTree.collapsePath(path);
        }
        else {
          myTree.expandPath(path);
        }
      }
    }
  }

  public void addOkAction(Runnable action) { myOkActions.add(action); }

  private void fireOkAction() {
    for (Runnable action : myOkActions) {
      action.run();
    }
  }

  public void registerMouseListener(final ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(myTree, group, ActionManager.getInstance());
  }

  private void addTreeExpansionListener() {
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeExpanded(final TreeExpansionEvent event) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            TreePath path = event.getPath();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            if (node.getUserObject() instanceof FileNodeDescriptor) {
              FileNodeDescriptor nodeDescriptor = (FileNodeDescriptor)node.getUserObject();
              FileElement fileDescriptor = nodeDescriptor.getElement();
              VirtualFile virtualFile = fileDescriptor.getFile();
              if (virtualFile != null) {
                virtualFile.refresh(false, false);
              }
            }
          }
        });
      }

      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });
  }

  public boolean areHiddensShown() {
    return myTreeStructure.areHiddensShown();
  }

  public void showHiddens(boolean showHiddens) {
    myTreeStructure.showHiddens(showHiddens);
    updateTree();
  }

  public void updateTree() {
    myTreeBuilder.updateFromRoot();
  }

  public void dispose() {
    if (myTreeBuilder != null) {
      Disposer.dispose(myTreeBuilder);
    }
  }

  public boolean select(final VirtualFile file) {
    DefaultMutableTreeNode node = getNodeForFile(file);
    if (node == null) return false;
    else {
      TreeUtil.selectPath(myTree, new TreePath(node.getPath()));
      return true;
    }
  }

  public boolean expand(VirtualFile directory) {
    if (!directory.isDirectory()) return false;
    DefaultMutableTreeNode node = getNodeForFile(directory);
    if (node == null) return false;
    myTree.expandPath(new TreePath(node.getPath()));
    return true;
  }

  private DefaultMutableTreeNode getNodeForFile(VirtualFile file) {
    VirtualFile selectFile;

    if ((file.getFileSystem() instanceof JarFileSystem) && file.getParent() == null) {
      selectFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (selectFile == null) {
        LOG.assertTrue(false, "nothing found for " + file.getPath());
        return null;
      }
    }
    else {
      selectFile = file;
    }

    FileElement descriptor = new FileElement(selectFile, selectFile.getName());

    myTreeBuilder.buildNodeForElement(descriptor);
    return myTreeBuilder.getNodeForElement(descriptor);
  }

  public Exception createNewFolder(final VirtualFile parentDirectory, final String newFolderName) {
    final Exception[] failReason = new Exception[] { null };
    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                try {
                  VirtualFile folder = parentDirectory.createChildDirectory(this, newFolderName);
                  updateTree();
                  FileElement folderDesc = new FileElement(folder, folder.getName());
                  myTreeBuilder.buildNodeForElement(folderDesc);
                  DefaultMutableTreeNode folderNode =
                      myTreeBuilder.getNodeForElement(folderDesc);
                  if (folderNode != null) {
                    TreePath treePath = new TreePath(folderNode.getPath());
                    myTree.setSelectionPath(treePath);
                    myTree.scrollPathToVisible(treePath);
                    myTree.expandPath(treePath);
                  }
                }
                catch (IOException e) {
                  failReason[0] = e;
                }
              }
            });
          }
        },
        UIBundle.message("file.chooser.create.new.folder.command.name"),
        null
    );
    return failReason[0];
  }

  public JTree getTree() { return myTree; }

  public VirtualFile getSelectedFile() {
    final TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (!(node.getUserObject() instanceof FileNodeDescriptor)) return null;
    FileNodeDescriptor descriptor = (FileNodeDescriptor)node.getUserObject();
    return descriptor.getElement().getFile();
  }

  public VirtualFile[] getSelectedFiles() {
    return collectSelectedFiles(new ConvertingIterator.IdConvertor<VirtualFile>());
  }

  public VirtualFile[] getChoosenFiles() {
    return collectSelectedFiles(new Convertor<VirtualFile, VirtualFile>() {
      public VirtualFile convert(VirtualFile file) {
        if (file == null || !file.isValid()) return null;
        return myTreeStructure.getChooserDescriptor().getFileToSelect(file);
      }
    });
  }

  private VirtualFile[] collectSelectedFiles(Convertor<VirtualFile, VirtualFile> fileConvertor) {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) return VirtualFile.EMPTY_ARRAY;
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>(paths.length);

    for (TreePath path : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (!(node.getUserObject() instanceof FileNodeDescriptor)) return VirtualFile.EMPTY_ARRAY;
      FileNodeDescriptor descriptor = (FileNodeDescriptor)node.getUserObject();
      VirtualFile file = fileConvertor.convert(descriptor.getElement().getFile());
      if (file != null && file.isValid()) files.add(file);
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public boolean selectionExists() {
    TreePath[] selectedPaths = myTree.getSelectionPaths();
    return selectedPaths != null && selectedPaths.length != 0;
  }

  public boolean isUnderRoots(VirtualFile file) {
    final java.util.List<VirtualFile> roots = myDescriptor.getRoots();
    if (roots.size() == 0) {
      return true;
    }
    for (VirtualFile root : roots) {
      if (VfsUtil.isAncestor(root, file, false)) {
        return true;
      }
    }
    return false;
  }
}
