package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SeparatorFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowser extends JPanel implements TypeSafeDataProvider {
  protected final ChangesTreeList<Change> myViewer;
  protected ChangeList mySelectedChangeList;
  protected Collection<Change> myChangesToDisplay;
  protected final Project myProject;
  private final boolean myCapableOfExcludingChanges;
  protected final JPanel myHeaderPanel;
  private DefaultActionGroup myToolBarGroup;
  private final JPanel myListPanel;
  private ShowDiffAction.DiffExtendUIFactory myDiffExtendUIFactory = new DiffToolbarActionsFactory();
  private String myToggleActionTitle = VcsBundle.message("commit.dialog.include.action.name");

  public static DataKey<ChangesBrowser> DATA_KEY = DataKey.create("com.intellij.openapi.vcs.changes.ui.ChangesBrowser");

  public void setChangesToDisplay(final List<Change> changes) {
    myChangesToDisplay = changes;
    myViewer.setChangesToDisplay(changes);
  }

  public ChangesBrowser(final Project project, List<? extends ChangeList> changeLists, final List<Change> changes,
                        ChangeList initialListSelection,
                        final boolean capableOfExcludingChanges, final boolean highlightProblems) {
    super(new BorderLayout());

    myProject = project;
    myCapableOfExcludingChanges = capableOfExcludingChanges;

    myViewer = new ChangesTreeList<Change>(myProject, changes, capableOfExcludingChanges, highlightProblems) {
      protected DefaultTreeModel buildTreeModel(final List<Change> changes) {
        TreeModelBuilder builder = new TreeModelBuilder(myProject, false);
        return builder.buildModel(changes);
      }

      protected List<Change> getSelectedObjects(final ChangesBrowserNode node) {
        return node.getAllChangesUnder();
      }
    };

    myViewer.setDoubleClickHandler(new Runnable() {
      public void run() {
        showDiff();
      }
    });

    setInitialSelection(changeLists, changes, initialListSelection);
    rebuildList();

    myListPanel = new JPanel(new BorderLayout());
    myListPanel.add(myViewer, BorderLayout.CENTER);

    JComponent separator = SeparatorFactory.createSeparator(VcsBundle.message("commit.dialog.changed.files.label"), myViewer);
    myListPanel.add(separator, BorderLayout.NORTH);
    add(myListPanel, BorderLayout.CENTER);

    myHeaderPanel = new JPanel(new BorderLayout());
    myHeaderPanel.add(createToolbar(), BorderLayout.CENTER);
    add(myHeaderPanel, BorderLayout.NORTH);

    myViewer.installPopupHandler(myToolBarGroup);
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    mySelectedChangeList = initialListSelection;
  }

  public void dispose() {
  }

  public void addToolbarAction(AnAction action) {
    myToolBarGroup.add(action);
  }

  public void addToolbarActions(ActionGroup group) {
    myToolBarGroup.addSeparator();
    myToolBarGroup.add(group);
  }

  public ShowDiffAction.DiffExtendUIFactory getDiffExtendUIFactory() {
    return myDiffExtendUIFactory;
  }

  public void setDiffExtendUIFactory(final ShowDiffAction.DiffExtendUIFactory diffExtendUIFactory) {
    myDiffExtendUIFactory = diffExtendUIFactory;
  }

  public void setToggleActionTitle(final String toggleActionTitle) {
    myToggleActionTitle = toggleActionTitle;
  }

  public JPanel getHeaderPanel() {
    return myHeaderPanel;
  }

  public JPanel getListPanel() {
    return myListPanel;
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == VcsDataKeys.CHANGES) {
      final List<Change> list = myViewer.getSelectedChanges();
      sink.put(VcsDataKeys.CHANGES, list.toArray(new Change [list.size()]));
    }
    else if (key == VcsDataKeys.CHANGE_LISTS) {
      sink.put(VcsDataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == PlatformDataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(PlatformDataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles());
    }
    else if (key == PlatformDataKeys.NAVIGATABLE_ARRAY) {
      sink.put(PlatformDataKeys.NAVIGATABLE_ARRAY, ChangesUtil.getNavigatableArray(myProject, getSelectedFiles()));
    }
    else if (key == DATA_KEY) {
      sink.put(DATA_KEY, this);
    }
  }

  private class ToggleChangeAction extends CheckboxAction {
    private final Change myChange;

    public ToggleChangeAction(final Change change) {
      super(myToggleActionTitle);
      myChange = change;
    }

    public boolean isSelected(AnActionEvent e) {
      return myViewer.isIncluded(myChange);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myViewer.includeChange(myChange);
      }
      else {
        myViewer.excludeChange(myChange);
      }
    }
  }

  protected void showDiffForChanges(Change[] changesArray, final int indexInSelection) {
    ShowDiffAction.showDiffForChange(changesArray, indexInSelection, myProject, myDiffExtendUIFactory, isInFrame());
  }

  private void showDiff() {
    final Change leadSelection = myViewer.getLeadSelection();
    List<Change> changes = myViewer.getSelectedChanges();

    if (changes.size() < 2) {
      final Collection<Change> displayedChanges = getCurrentDisplayedChanges();
      changes = new ArrayList<Change>(displayedChanges);
    }

    int indexInSelection = changes.indexOf(leadSelection);
    if (indexInSelection >= 0) {
      Change[] changesArray = changes.toArray(new Change[changes.size()]);
      showDiffForChanges(changesArray, indexInSelection);
    }
    else if ((leadSelection == null && changes.size() > 0)) {
      Change[] changesArray = changes.toArray(new Change[changes.size()]);
      showDiffForChanges(changesArray, 0);
    }
    else if (leadSelection != null) {
      showDiffForChanges(new Change[]{leadSelection}, 0);
    }
  }

  private static boolean isInFrame() {
    return ModalityState.current().equals(ModalityState.NON_MODAL);
  }

  private class DiffToolbarActionsFactory implements ShowDiffAction.DiffExtendUIFactory {
    public List<? extends AnAction> createActions(Change change) {
      return createDiffActions(change);
    }

    @Nullable
    public JComponent createBottomComponent() {
      return null;
    }
  }

  protected List<AnAction> createDiffActions(final Change change) {
    List<AnAction> actions = new ArrayList<AnAction>();
    if (myCapableOfExcludingChanges) {
      actions.add(new ToggleChangeAction(change));
    }
    return actions;
  }

  public void rebuildList() {
    myViewer.setChangesToDisplay(getCurrentDisplayedChanges());
  }

  private JComponent createToolbar() {
    DefaultActionGroup toolbarGroups = new DefaultActionGroup();
    myToolBarGroup = new DefaultActionGroup();
    toolbarGroups.add(myToolBarGroup);
    buildToolBar(myToolBarGroup);

    toolbarGroups.addSeparator();
    DefaultActionGroup treeActionsGroup = new DefaultActionGroup();
    toolbarGroups.add(treeActionsGroup);
    for(AnAction action: myViewer.getTreeActions()) {
      treeActionsGroup.add(action);
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroups, true).getComponent();
  }

  protected void buildToolBar(final DefaultActionGroup toolBarGroup) {
    final ShowDiffAction diffAction = new ShowDiffAction() {
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    };

    diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myViewer);
    toolBarGroup.add(diffAction);
  }

  public List<Change> getCurrentDisplayedChanges() {
    final List<Change> list;
    if (myChangesToDisplay != null) {
      list = new ArrayList<Change>(myChangesToDisplay);
    }
    else if (mySelectedChangeList != null) {
      list = new ArrayList<Change>(mySelectedChangeList.getChanges());
    }
    else {
      list = Collections.emptyList();
    }
    return sortChanges(list);
  }

  protected static List<Change> sortChanges(final List<Change> list) {
    Collections.sort(list, new Comparator<Change>() {
      public int compare(final Change o1, final Change o2) {
        return ChangesUtil.getFilePath(o1).getName().compareToIgnoreCase(ChangesUtil.getFilePath(o2).getName());
      }
    });
    return list;
  }

  public ChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  public JComponent getPrefferedFocusComponent() {
    return myViewer;
  }

  private ChangeList[] getSelectedChangeLists() {
    if (mySelectedChangeList != null) {
      return new ChangeList[]{mySelectedChangeList};
    }
    return null;
  }

  private VirtualFile[] getSelectedFiles() {
    final List<Change> changes = myViewer.getSelectedChanges();
    return ChangesUtil.getFilesFromChanges(changes);
  }

}
