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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class ShelvedChangesViewManager implements ProjectComponent {
  private ToolWindowManagerEx myToolWindowManager;
  private ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  private ChangesViewManager myChangesViewManager;
  private ToolWindowManagerListener myToolWindowManagerListener = new MyToolWindowManagerListener();
  private Tree myTree = new ShelfTree();
  private Content myContent = null;

  public static DataKey<ShelveChangesManager.ShelvedChangeListData[]> SHELVED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedChangeListData");

  public ShelvedChangesViewManager(Project project, ToolWindowManagerEx toolWindowManager, ShelveChangesManager shelveChangesManager,
                                   final ChangesViewManager changesViewManager, final MessageBus bus) {
    myProject = project;
    myChangesViewManager = changesViewManager;
    myToolWindowManager = toolWindowManager;
    myShelveChangesManager = shelveChangesManager;
    bus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateChangesContent();
      }
    });

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer());

    final CustomShortcutSet diffShortcut =
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK));
    ActionManager.getInstance().getAction("ChangesView.Diff").registerCustomShortcutSet(diffShortcut, myTree);

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

  private class ShelfTree extends Tree implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        final List<ShelveChangesManager.ShelvedChangeListData> list =
          TreeUtil.collectSelectedObjectsOfType(this, ShelveChangesManager.ShelvedChangeListData.class);
        if (list != null) {
          sink.put(SHELVED_CHANGELIST_KEY, list.toArray(new ShelveChangesManager.ShelvedChangeListData[list.size()]));
        }
      }
      else if (key == DataKeys.CHANGES) {
        List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        if (shelvedChanges.size() > 0) {
          Change[] changes = new Change[shelvedChanges.size()];
          for(int i=0; i<shelvedChanges.size(); i++) {
            changes [i] = shelvedChanges.get(i).getChange(myProject);
          }
          sink.put(DataKeys.CHANGES, changes);
        }
        else {
          final List<ShelveChangesManager.ShelvedChangeListData> changeLists =
            TreeUtil.collectSelectedObjectsOfType(this, ShelveChangesManager.ShelvedChangeListData.class);
          if (changeLists.size() > 0) {
            List<Change> changes = new ArrayList<Change>();
            for(ShelveChangesManager.ShelvedChangeListData changeList: changeLists) {
              shelvedChanges = changeList.getChanges();
              for(ShelvedChange shelvedChange: shelvedChanges) {
                changes.add(shelvedChange.getChange(myProject));
              }
            }
            sink.put(DataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
          }
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
        append(change.getFileName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, change.getFileStatus().getColor()));
        append(" ("+ change.getPatchedFilePath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(FileTypeManager.getInstance().getFileTypeByFileName(change.getFileName()).getIcon());
      }
    }
  }
}