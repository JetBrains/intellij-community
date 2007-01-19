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

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
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
import javax.swing.tree.TreePath;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ShelvedChangesViewManager implements ProjectComponent {
  private ChangesViewContentManager myContentManager;
  private ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  private Tree myTree = new ShelfTree();
  private Content myContent = null;
  private ShelvedChangeDeleteProvider myDeleteProvider = new ShelvedChangeDeleteProvider();

  public static DataKey<ShelvedChangeList[]> SHELVED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedChangeListData");
  public static DataKey<List<ShelvedChange>> SHELVED_CHANGE_KEY = DataKey.create("ShelveChangesManager.ShelvedChange");

  public ShelvedChangesViewManager(Project project, ChangesViewContentManager contentManager, ShelveChangesManager shelveChangesManager,
                                   final MessageBus bus) {
    myProject = project;
    myContentManager = contentManager;
    myShelveChangesManager = shelveChangesManager;
    bus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            updateChangesContent();
          }
        }, ModalityState.NON_MODAL);
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
    updateChangesContent();
  }

  public void projectClosed() {
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
    final List<ShelvedChangeList> changes = myShelveChangesManager.getShelvedChangeLists();
    if (changes.size() == 0) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        myContent = PeerFactory.getInstance().getContentFactory().createContent(new JScrollPane(myTree), "Shelf", false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
      myTree.setModel(buildChangesModel());
    }
  }

  private TreeModel buildChangesModel() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    final List<ShelvedChangeList> changeLists = myShelveChangesManager.getShelvedChangeLists();
    for(ShelvedChangeList changeListData: changeLists) {
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

  private class ShelfTree extends Tree implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        final TreePath[] selections = getSelectionPaths();
        final Set<ShelvedChangeList> changeLists = new HashSet<ShelvedChangeList>();
        for(TreePath path: selections) {
          if (path.getPathCount() >= 2) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(1);
            if (node.getUserObject() instanceof ShelvedChangeList) {
              changeLists.add((ShelvedChangeList) node.getUserObject());
            }
          }
        }
        
        if (changeLists.size() > 0) {
          sink.put(SHELVED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_CHANGE_KEY) {
        final List<ShelvedChange> list = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        if (list != null) {
          sink.put(SHELVED_CHANGE_KEY, list);
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
          final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
          if (changeLists.size() > 0) {
            List<Change> changes = new ArrayList<Change>();
            for(ShelvedChangeList changeList: changeLists) {
              shelvedChanges = changeList.getChanges();
              for(ShelvedChange shelvedChange: shelvedChanges) {
                changes.add(shelvedChange.getChange(myProject));
              }
            }
            sink.put(DataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
          }
        }
      }
      else if (key == DataKeys.DELETE_ELEMENT_PROVIDER) {
        sink.put(DataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
      }
    }
  }

  private static class ShelfTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelvedChangeList) {
        ShelvedChangeList changeListData = (ShelvedChangeList) nodeValue;
        append(changeListData.DESCRIPTION, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        final String date = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(changeListData.DATE);
        append(" (" + date + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(StdFileTypes.PATCH.getIcon());
      }
      else if (nodeValue instanceof ShelvedChange) {
        ShelvedChange change = (ShelvedChange) nodeValue;
        append(change.getBeforeFileName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, change.getFileStatus().getColor()));
        append(" ("+ change.getBeforeDirectory() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(FileTypeManager.getInstance().getFileTypeByFileName(change.getBeforeFileName()).getIcon());
      }
    }
  }

  private class ShelvedChangeDeleteProvider implements DeleteProvider {
    public void deleteElement(DataContext dataContext) {
      //noinspection unchecked
      ShelvedChangeList[] shelvedChangeLists = (ShelvedChangeList[]) dataContext.getData(SHELVED_CHANGELIST_KEY.getName());
      if (shelvedChangeLists == null || shelvedChangeLists.length == 0) return;
      String message = (shelvedChangeLists.length == 1)
        ? VcsBundle.message("shelve.changes.delete.confirm", shelvedChangeLists[0].DESCRIPTION)
        : VcsBundle.message("shelve.changes.delete.multiple.confirm", shelvedChangeLists.length);
      int rc = Messages.showOkCancelDialog(myProject, message, VcsBundle.message("shelvedChanges.delete.title"), Messages.getWarningIcon());
      if (rc != 0) return;
      for(ShelvedChangeList changeList: shelvedChangeLists) {
        ShelveChangesManager.getInstance(myProject).deleteChangeList(changeList);
      }
    }

    public boolean canDeleteElement(DataContext dataContext) {
      //noinspection unchecked
      ShelvedChangeList[] shelvedChangeLists = (ShelvedChangeList[]) dataContext.getData(SHELVED_CHANGELIST_KEY.getName());
      return shelvedChangeLists != null && shelvedChangeLists.length > 0;
    }
  }
}