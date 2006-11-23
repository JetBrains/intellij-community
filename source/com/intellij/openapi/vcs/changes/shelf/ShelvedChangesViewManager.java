/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 15:11:11
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.util.ui.Tree;
import com.intellij.util.messages.MessageBus;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;

public class ShelvedChangesViewManager implements ProjectComponent {
  private ToolWindowManagerEx myToolWindowManager;
  private ShelveChangesManager myShelveChangesManager;
  private ChangesViewManager myChangesViewManager;
  private ToolWindowManagerListener myToolWindowManagerListener = new MyToolWindowManagerListener();
  private Tree myTree = new ShelfTree();
  private Content myContent = null;
  private MessageBus myBus;

  public static DataKey<ShelveChangesManager.ShelvedChangeListData[]> SHELVED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedChangeListData");

  public ShelvedChangesViewManager(final ToolWindowManagerEx toolWindowManager, final ShelveChangesManager shelveChangesManager,
                                   final ChangesViewManager changesViewManager, final MessageBus bus) {
    myChangesViewManager = changesViewManager;
    myToolWindowManager = toolWindowManager;
    myShelveChangesManager = shelveChangesManager;
    myBus = bus;
    myBus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateChangesContent();
      }
    });

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer());
    PopupHandler.installPopupHandler(myTree, "ShelvedChangesPopupMenu", ActionPlaces.UNKNOWN);
  }

  public void projectOpened() {
    myToolWindowManager.addToolWindowManagerListener(myToolWindowManagerListener);
  }

  public void projectClosed() {
    myToolWindowManager.removeToolWindowManagerListener(myToolWindowManagerListener);
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ShelvedChangesViewManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void updateChangesContent() {
    final ContentManager contentManager = myChangesViewManager.getContentManager();
    if (contentManager == null) {
      return;
    }
    final List<ShelveChangesManager.ShelvedChangeListData> changes = myShelveChangesManager.getShelvedChangeLists();
    if (changes.size() == 0) {
      if (myContent != null) {
        contentManager.removeContent(myContent);
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        myContent = PeerFactory.getInstance().getContentFactory().createContent(myTree, "Shelf", false);
        contentManager.addContent(myContent);
      }
      myTree.setModel(buildChangesModel());
    }
  }

  private TreeModel buildChangesModel() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    final List<ShelveChangesManager.ShelvedChangeListData> changeLists = myShelveChangesManager.getShelvedChangeLists();
    for(ShelveChangesManager.ShelvedChangeListData changeListData: changeLists) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(changeListData);
      model.insertNodeInto(node, root, root.getChildCount());

      List<ShelvedChange> changes = changeListData.getChanges();
      for(ShelvedChange change: changes) {
        DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(change);
        model.insertNodeInto(pathNode, node, node.getChildCount());
      }
    }
    return model;
  }

  private class MyToolWindowManagerListener implements ToolWindowManagerListener {
    public void toolWindowRegistered(String id) {
      if (id.equals(ChangesViewManager.TOOLWINDOW_ID)) {
        updateChangesContent();
      }
    }

    public void stateChanged() {
    }
  }

  private static class ShelfTree extends Tree implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        List<ShelveChangesManager.ShelvedChangeListData> result = new ArrayList<ShelveChangesManager.ShelvedChangeListData>();
        final TreePath[] treePaths = getSelectionPaths();
        for(TreePath treePath: treePaths) {
          if (treePath.getLastPathComponent() instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            if (node.getUserObject() instanceof ShelveChangesManager.ShelvedChangeListData) {
              result.add((ShelveChangesManager.ShelvedChangeListData) node.getUserObject());
            }
          }
        }
        if (!result.isEmpty()) {
          sink.put(SHELVED_CHANGELIST_KEY, result.toArray(new ShelveChangesManager.ShelvedChangeListData[result.size()]));
        }
      }
    }
  }

  private static class ShelfTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelveChangesManager.ShelvedChangeListData) {
        ShelveChangesManager.ShelvedChangeListData changeListData = (ShelveChangesManager.ShelvedChangeListData) nodeValue;
        append(changeListData.DESCRIPTION, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        final String date = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(changeListData.DATE);
        append(" (" + date + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(StdFileTypes.PATCH.getIcon());
      }
      else if (nodeValue instanceof ShelvedChange) {
        ShelvedChange change = (ShelvedChange) nodeValue;
        append(change.getFilePath(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, change.getFileStatus().getColor()));
        setIcon(FileTypeManager.getInstance().getFileTypeByFileName(change.getFileName()).getIcon());
      }
    }
  }
}