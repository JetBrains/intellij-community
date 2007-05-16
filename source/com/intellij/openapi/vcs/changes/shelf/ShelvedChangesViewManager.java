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

import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
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
import javax.swing.tree.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShelvedChangesViewManager implements ProjectComponent {
  private ChangesViewContentManager myContentManager;
  private ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  private Tree myTree = new ShelfTree();
  private Content myContent = null;
  private ShelvedChangeDeleteProvider myDeleteProvider = new ShelvedChangeDeleteProvider();
  private boolean myUpdatePending = false;
  private Runnable myPostUpdateRunnable = null;

  public static DataKey<ShelvedChangeList[]> SHELVED_CHANGELIST_KEY = DataKey.create("ShelveChangesManager.ShelvedChangeListData");
  public static DataKey<List<ShelvedChange>> SHELVED_CHANGE_KEY = DataKey.create("ShelveChangesManager.ShelvedChange");
  public static DataKey<List<ShelvedBinaryFile>> SHELVED_BINARY_FILE_KEY = DataKey.create("ShelveChangesManager.ShelvedBinaryFile");
  private static final Object ROOT_NODE_VALUE = new Object();
  private DefaultMutableTreeNode myRoot;

  public static ShelvedChangesViewManager getInstance(Project project) {
    return project.getComponent(ShelvedChangesViewManager.class);
  }

  public ShelvedChangesViewManager(Project project, ChangesViewContentManager contentManager, ShelveChangesManager shelveChangesManager,
                                   final MessageBus bus) {
    myProject = project;
    myContentManager = contentManager;
    myShelveChangesManager = shelveChangesManager;
    bus.connect().subscribe(ShelveChangesManager.SHELF_TOPIC, new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myUpdatePending = true;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            updateChangesContent();
          }
        }, ModalityState.NON_MODAL);
      }
    });

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer(project));
    new TreeLinkMouseListener(new ShelfTreeCellRenderer(project)).install(myTree);

    ActionManager.getInstance().getAction("ChangesView.Diff").registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);

    PopupHandler.installPopupHandler(myTree, "ShelvedChangesPopupMenu", ActionPlaces.UNKNOWN);

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (myTree.getPathForLocation(e.getX(), e.getY()) == null) return;
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath == null) return;
        final Object lastPathComponent = selectionPath.getLastPathComponent();
        if (((TreeNode) lastPathComponent).isLeaf()) {
          DataContext context = DataManager.getInstance().getDataContext(myTree);
          final Change[] changes = DataKeys.CHANGES.getData(context);
          ShowDiffAction.showDiffForChange(changes, 0, myProject);
          e.consume();
        }
      }
    });
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
    myUpdatePending = false;
    final List<ShelvedChangeList> changes = myShelveChangesManager.getShelvedChangeLists();
    if (changes.size() == 0) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        myContent = PeerFactory.getInstance().getContentFactory().createContent(new JScrollPane(myTree), VcsBundle.message("shelf.tab"), false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
      TreeState state = TreeState.createOn(myTree);
      myTree.setModel(buildChangesModel());
      state.applyTo(myTree);
      if (myPostUpdateRunnable != null) {
        myPostUpdateRunnable.run();
      }      
    }
    myPostUpdateRunnable = null;
  }

  private TreeModel buildChangesModel() {
    myRoot = new DefaultMutableTreeNode(ROOT_NODE_VALUE);   // not null for TreeState matching to work
    DefaultTreeModel model = new DefaultTreeModel(myRoot);
    final List<ShelvedChangeList> changeLists = myShelveChangesManager.getShelvedChangeLists();
    for(ShelvedChangeList changeList: changeLists) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(changeList);
      model.insertNodeInto(node, myRoot, myRoot.getChildCount());

      List<ShelvedChange> changes = changeList.getChanges();
      for(ShelvedChange change: changes) {
        DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(change);
        model.insertNodeInto(pathNode, node, node.getChildCount());
      }
      List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
      for(ShelvedBinaryFile file: binaryFiles) {
        DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(file);
        model.insertNodeInto(pathNode, node, node.getChildCount());
      }
    }
    return model;
  }

  public void activateView(final ShelvedChangeList list) {
    Runnable runnable = new Runnable() {
      public void run() {
        if (list != null) {
          TreeUtil.selectNode(myTree, TreeUtil.findNodeWithObject(myRoot, list));
        }
        myContentManager.setSelectedContent(myContent);
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
        if (!window.isVisible()) {
          window.activate(null);
        }
      }
    };
    if (myUpdatePending) {
      myPostUpdateRunnable = runnable;
    }
    else {
      runnable.run();
    }
  }

  private class ShelfTree extends Tree implements TypeSafeDataProvider {
    public void calcData(DataKey key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        final TreePath[] selections = getSelectionPaths();
        final Set<ShelvedChangeList> changeLists = new HashSet<ShelvedChangeList>();
        if (selections != null) {
          for(TreePath path: selections) {
            if (path.getPathCount() >= 2) {
              DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getPathComponent(1);
              if (node.getUserObject() instanceof ShelvedChangeList) {
                changeLists.add((ShelvedChangeList) node.getUserObject());
              }
            }
          }
        }

        if (changeLists.size() > 0) {
          sink.put(SHELVED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_CHANGE_KEY) {
        sink.put(SHELVED_CHANGE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
      }
      else if (key == SHELVED_BINARY_FILE_KEY) {
        sink.put(SHELVED_BINARY_FILE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class));
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
    private IssueLinkRenderer myIssueLinkRenderer;

    public ShelfTreeCellRenderer(Project project) {
      myIssueLinkRenderer = new IssueLinkRenderer(project, this);
    }

    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelvedChangeList) {
        ShelvedChangeList changeListData = (ShelvedChangeList) nodeValue;
        myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION);
        final String date = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(changeListData.DATE);
        append(" (" + date + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(StdFileTypes.PATCH.getIcon());
      }
      else if (nodeValue instanceof ShelvedChange) {
        ShelvedChange change = (ShelvedChange) nodeValue;
        renderFileName(change.getBeforePath(), change.getFileStatus());
      }
      else if (nodeValue instanceof ShelvedBinaryFile) {
        ShelvedBinaryFile binaryFile = (ShelvedBinaryFile) nodeValue;
        String path = binaryFile.BEFORE_PATH;
        if (path == null) {
          path = binaryFile.AFTER_PATH;
        }
        renderFileName(path, binaryFile.getFileStatus());
      }
    }

    private void renderFileName(String path, final FileStatus fileStatus) {
      path = path.replace('/', File.separatorChar);
      int pos = path.lastIndexOf(File.separatorChar);
      String fileName;
      String directory;
      if (pos >= 0) {
        directory = path.substring(0, pos).replace(File.separatorChar, File.separatorChar);
        fileName = path.substring(pos+1);
      }
      else {
        directory = "<project root>";
        fileName = path;
      }
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor()));
      append(" ("+ directory + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon());
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