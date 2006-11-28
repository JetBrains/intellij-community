package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowser extends JPanel implements TypeSafeDataProvider {
  protected ChangesTreeList myViewer;
  protected ChangeList mySelectedChangeList;
  protected Collection<Change> myChangesToDisplay;
  protected Project myProject;
  protected JPanel myHeaderPanel;
  protected boolean myReadOnly;
  private DefaultActionGroup myToolBarGroup;

  public void setChangesToDisplay(final List<Change> changes) {
    myChangesToDisplay = changes;
    myViewer.setChangesToDisplay(changes);
  }

  @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";

  public ChangesBrowser(final Project project, List<? extends ChangeList> changeLists, final List<Change> changes,
                        ChangeList initialListSelection, final boolean showChangelistChooser,
                        final boolean capableOfExcludingChanges) {
    super(new BorderLayout());

    myProject = project;
    myReadOnly = !showChangelistChooser;

    myViewer = new ChangesTreeList(myProject, changes, capableOfExcludingChanges);
    myViewer.setDoubleClickHandler(new Runnable() {
      public void run() {
        showDiff();
      }
    });

    setInitialSelection(changeLists, changes, initialListSelection);
    rebuildList();

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(myViewer);
    listPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("commit.dialog.changed.files.label")));
    add(listPanel, BorderLayout.CENTER);

    myHeaderPanel = new JPanel(new BorderLayout());
    myHeaderPanel.add(createToolbar(), BorderLayout.WEST);
    add(myHeaderPanel, BorderLayout.NORTH);

    myViewer.installPopupHandler(myToolBarGroup);

    myViewer.setShowFlatten(PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY));
  }

  protected void setInitialSelection(final List<? extends ChangeList> changeLists, final List<Change> changes, final ChangeList initialListSelection) {
    mySelectedChangeList = initialListSelection;
  }

  public void dispose() {
  }

  public void addRollbackAction() {
    myToolBarGroup.add(new RollbackAction());
  }

  public void addToolbarAction(AnAction action) {
    myToolBarGroup.add(action);
  }

  public void addToolbarActions(ActionGroup group) {
    myToolBarGroup.addSeparator();
    myToolBarGroup.add(group);
  }

  public JPanel getHeaderPanel() {
    return myHeaderPanel;
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == DataKeys.CHANGES) {
      sink.put(DataKeys.CHANGES, myViewer.getSelectedChanges());
    }
    else if (key == DataKeys.CHANGE_LISTS) {
      sink.put(DataKeys.CHANGE_LISTS, getSelectedChangeLists());
    }
    else if (key == DataKeys.VIRTUAL_FILE_ARRAY) {
      sink.put(DataKeys.VIRTUAL_FILE_ARRAY, getSelectedFiles());
    }
    else if (key == DataKeys.NAVIGATABLE_ARRAY) {
      sink.put(DataKeys.NAVIGATABLE_ARRAY, ChangesUtil.getNavigatableArray(myProject, getSelectedFiles()));
    }
  }

  private class MoveAction extends MoveChangesToAnotherListAction {
    private final Change myChange;

    public MoveAction(final Change change) {
      myChange = change;
    }

    public void actionPerformed(AnActionEvent e) {
      askAndMove(myProject, new Change[]{myChange}, null);
    }
  }

  private class ToggleChangeAction extends CheckboxAction {
    private final Change myChange;

    public ToggleChangeAction(final Change change) {
      super(VcsBundle.message("commit.dialog.include.action.name"));
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

  private void showDiff() {
    final Change leadSelection = myViewer.getLeadSelection();
    Change[] changes = myViewer.getSelectedChanges();

    if (changes.length < 2) {
      final Collection<Change> displayedChanges = getCurrentDisplayedChanges();
      changes = displayedChanges.toArray(new Change[displayedChanges.size()]);
    }

    int indexInSelection = Arrays.asList(changes).indexOf(leadSelection);
    if (indexInSelection >= 0) {
      ShowDiffAction.showDiffForChange(changes, indexInSelection, myProject, !myReadOnly ? new DiffToolbarActionsFactory() : null);
    }
    else {
      ShowDiffAction.showDiffForChange(new Change[]{leadSelection}, 0, myProject, !myReadOnly ? new DiffToolbarActionsFactory() : null);
    }
  }

  private class DiffToolbarActionsFactory implements ShowDiffAction.AdditionalToolbarActionsFactory {
    public List<? extends AnAction> createActions(Change change) {
      return Arrays.asList(new MoveAction(change), new ToggleChangeAction(change));
    }
  }

  protected void rebuildList() {
    myViewer.setChangesToDisplay(getCurrentDisplayedChanges());
  }

  private JComponent createToolbar() {
    myToolBarGroup = new DefaultActionGroup();
    final ShowDiffAction diffAction = new ShowDiffAction() {
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    };

    diffAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      myViewer);
    myToolBarGroup.add(diffAction);

    if (!myReadOnly) {
      final MoveChangesToAnotherListAction moveAction = new MoveChangesToAnotherListAction() {
        public void actionPerformed(AnActionEvent e) {
          super.actionPerformed(e);
          rebuildList();
        }
      };

      moveAction.registerCustomShortcutSet(CommonShortcuts.getMove(), myViewer);
      myToolBarGroup.add(moveAction);
    }

    ToggleShowDirectoriesAction directoriesAction = new ToggleShowDirectoriesAction();
    directoriesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      myViewer);

    myToolBarGroup.add(directoriesAction);

    for(AnAction action: myViewer.getTreeActions()) {
      myToolBarGroup.add(action);
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, myToolBarGroup, true).getComponent();
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
    return new ChangeList[]{mySelectedChangeList};
  }

  private VirtualFile[] getSelectedFiles() {
    final Change[] changes = myViewer.getSelectedChanges();
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile file = afterRevision.getFile().getVirtualFile();
        if (file != null && file.isValid()) {
          files.add(file);
        }
      }
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public class ToggleShowDirectoriesAction extends ToggleAction {
    public ToggleShowDirectoriesAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(myProject).setValue(FLATTEN_OPTION_KEY, String.valueOf(!state));
      myViewer.setShowFlatten(!state);
    }
  }

  public class RollbackAction extends AnAction {
    public RollbackAction() {
      super(VcsBundle.message("changes.action.rollback.text"), VcsBundle.message("changes.action.rollback.description"),
            IconLoader.getIcon("/actions/rollback.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      Change[] changes = e.getData(DataKeys.CHANGES);
      RollbackChangesDialog.rollbackChanges(myProject, Arrays.asList(changes), true);
      ChangeListManager.getInstance(myProject).ensureUpToDate(false);
      rebuildList();
    }

    public void update(AnActionEvent e) {
      Change[] changes = e.getData(DataKeys.CHANGES);
      e.getPresentation().setEnabled(changes != null);
    }
  }
}
