/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PanelWithActionsAndCloseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UIHelper;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class UpdateInfoTree extends PanelWithActionsAndCloseButton {
  private VirtualFile mySelectedFile;
  protected JTree myTree = new Tree();
  protected final Project myProject;
  protected final UpdatedFiles myUpdatedFiles;
  private UpdateRootNode myRoot;
  private DefaultTreeModel myTreeModel;
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.updateinfo.CvsUpdateInfoTree");
  protected FileStatusListener myFileStatusListener;
  protected final FileStatusManager myFileStatusManager;
  private final String myRootName;
  private final ActionInfo myActionInfo;

  public UpdateInfoTree(ContentManager contentManager,
                        String helpId,
                        Project project,
                        UpdatedFiles updatedFiles,
                        String rootName,
                        ActionInfo actionInfo) {
    super(contentManager, helpId);
    myActionInfo = actionInfo;

    myFileStatusListener = new FileStatusListener() {
      public void fileStatusesChanged() {
        myTree.repaint();
      }

      public void fileStatusChanged(VirtualFile virtualFile) {
        myTree.repaint();
      }
    };

    myProject = project;
    myUpdatedFiles = updatedFiles;
    myRootName = rootName;

    myFileStatusManager = FileStatusManager.getInstance(myProject);
    myFileStatusManager.addFileStatusListener(myFileStatusListener);
    createTree();
    init();
  }

  protected void dispose() {
    super.dispose();
    myFileStatusManager.removeFileStatusListener(myFileStatusListener);
  }

  protected void addActionsTo(DefaultActionGroup group) {
    group.add(new MyGroupByPackagesAction());
  }

  protected JComponent createCenterPanel() {
    return ScrollPaneFactory.createScrollPane(myTree);
  }

  protected void createTree() {
    UIHelper uiHelper = PeerFactory.getInstance().getUIHelper();
    uiHelper.installSmartExpander(myTree);
    uiHelper.installSelectionSaver(myTree);
    refreshTree();

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        AbstractTreeNode treeNode = (AbstractTreeNode)e.getPath().getLastPathComponent();
        if (treeNode instanceof FileTreeNode) {
          mySelectedFile = ((FileTreeNode)treeNode).getFilePointer().getFile();
        }
        else {
          mySelectedFile = null;
        }
      }
    });
    myTree.setCellRenderer(new UpdateTreeCellRenderer());
    uiHelper.installToolTipHandler(myTree);
    TreeUtil.installActions(myTree);

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP,
                                                                                      getActionGroup());
        popupMenu.getComponent().show(comp, x, y);
      }
    });
    uiHelper.installEditSourceOnDoubleClick(myTree);
    uiHelper.installEditSourceOnEnterKeyHandler(myTree);

    myTree.setSelectionRow(0);
  }

  private void refreshTree() {
    LOG.assertTrue(myProject != null);
    myRoot = new UpdateRootNode(myUpdatedFiles, myProject, myRootName, myActionInfo);
    myRoot.rebuild(VcsConfiguration.getInstance(myProject).UPDATE_GROUP_BY_PACKAGES);
    myTreeModel = new DefaultTreeModel(myRoot);
    myRoot.setTreeModel(myTreeModel);
    myTree.setModel(myTreeModel);
    myRoot.setTree(myTree);
  }

  private DefaultActionGroup getActionGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction("UpdateActionGroup");
  }

  public Object getData(String dataId) {
    if (DataConstants.NAVIGATABLE.equals(dataId)) {
      if (mySelectedFile == null) return null;
      return new OpenFileDescriptor(myProject, mySelectedFile);
    }
    else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
      return getVirtualFileArray();
    }
    else if (VcsDataConstants.IO_FILE_ARRAY.equals(dataId)) {
      return getFileArray();
    }
    return ProjectLevelVcsManager.getInstance(myProject).createVirtualAndPsiFileDataProvider(getVirtualFileArray(), mySelectedFile)
      .getData(dataId);
  }

  private VirtualFile[] getVirtualFileArray() {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths != null) {
      for (int i = 0; i < selectionPaths.length; i++) {
        TreePath selectionPath = selectionPaths[i];
        AbstractTreeNode treeNode = (AbstractTreeNode)selectionPath.getLastPathComponent();
        result.addAll(treeNode.getVirtualFiles());
      }
    }
    if (result.isEmpty()) return VirtualFile.EMPTY_ARRAY;
    return (VirtualFile[])result.toArray(new VirtualFile[result.size()]);
  }

  protected File[] getFileArray() {
    ArrayList<File> result = new ArrayList<File>();
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths != null) {
      for (int i = 0; i < selectionPaths.length; i++) {
        TreePath selectionPath = selectionPaths[i];
        AbstractTreeNode treeNode = (AbstractTreeNode)selectionPath.getLastPathComponent();
        result.addAll(treeNode.getFiles());
      }
    }
    if (result.isEmpty()) return null;
    return (File[])result.toArray(new File[result.size()]);
  }

  public void expandRootChildren() {
    TreeNode root = (TreeNode)myTreeModel.getRoot();

    if (root.getChildCount() == 1) {
      myTree.expandPath(new TreePath(new Object[]{root, root.getChildAt(0)}));
    }
  }

  private class MyGroupByPackagesAction extends ToggleAction {
    public MyGroupByPackagesAction() {
      super("Group by Packages", null, IconLoader.getIcon("/_cvs/showAsTree.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return VcsConfiguration.getInstance(myProject).UPDATE_GROUP_BY_PACKAGES;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      VcsConfiguration.getInstance(myProject).UPDATE_GROUP_BY_PACKAGES = state;
      myRoot.rebuild(VcsConfiguration.getInstance(myProject).UPDATE_GROUP_BY_PACKAGES);
    }
  }
}
