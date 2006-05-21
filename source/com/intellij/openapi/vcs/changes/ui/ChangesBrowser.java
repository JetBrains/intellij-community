package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowser extends JPanel implements DataProvider {
  private ChangesTreeList myViewer;
  private ChangeList mySelectedChangeList;
  private Collection<Change> myAllChanges;
  private final Map<Change, LocalChangeList> myChangeListsMap = new HashMap<Change, LocalChangeList>();
  private Project myProject;
  @Nullable private final ActionGroup myAdditionalToolbarActions;
  private EventDispatcher<SelectedListChangeListener> myDispatcher = EventDispatcher.create(SelectedListChangeListener.class);
  private final JPanel myHeaderPanel;

  public void setChangesToDisplay(final List<Change> changes) {
    myViewer.setChangesToDisplay(changes);
  }

  public interface SelectedListChangeListener extends EventListener{
    void selectedListChanged();
  }

  public void addSelectedListChangeListener(SelectedListChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeSelectedListChangeListener(SelectedListChangeListener listener) {
    myDispatcher.removeListener(listener);
  }

  @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";

  public ChangesBrowser(final Project project, List<? extends ChangeList> changeLists, final List<Change> changes,
                        final @Nullable ActionGroup additionalToolbarActions, final boolean showChangelistChooser,
                        final boolean capableOfExcludingChanges) {
    super(new BorderLayout());

    myProject = project;
    myAdditionalToolbarActions = additionalToolbarActions;
    myAllChanges = new ArrayList<Change>();

    ChangeList initalListSelection = null;
    for (ChangeList list : changeLists) {
      myAllChanges.addAll(list.getChanges());
      if (list instanceof LocalChangeList && ((LocalChangeList)list).isDefault()) {
        initalListSelection = list;
      }
    }

    if (initalListSelection == null) {
      initalListSelection = changeLists.get(0);
    }

    myViewer = new ChangesTreeList(myProject, changes, capableOfExcludingChanges);
    myViewer.setDoubleClickHandler(new Runnable() {
      public void run() {
        showDiff();
      }
    });

    setSelectedList(initalListSelection);

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(myViewer);
    listPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("commit.dialog.changed.files.label")));
    add(listPanel, BorderLayout.CENTER);

    myHeaderPanel = new JPanel(new BorderLayout());
    if (showChangelistChooser) {
      myHeaderPanel.add(new ChangeListChooser(changeLists), BorderLayout.EAST);
    }
    myHeaderPanel.add(createToolbar(), BorderLayout.WEST);
    add(myHeaderPanel, BorderLayout.NORTH);

    myViewer.setShowFlatten(PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY));
  }

  public JPanel getHeaderPanel() {
    return myHeaderPanel;
  }

  public JComponent getContentComponent() {
    return this;
  }

  public Collection<Change> getAllChanges() {
    return myAllChanges;
  }

  public Object getData(final String dataId) {
    if (DataConstants.CHANGES.equals(dataId)) {
      return myViewer.getSelectedChanges();
    }
    else if (DataConstants.CHANGE_LISTS.equals(dataId)) {
      return getSelectedChangeLists();
    }
    else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
      return getSelectedFiles();
    }

    return null;
  }

  private class MoveAction extends MoveChangesToAnotherListAction {
    private final Change myChange;


    public MoveAction(final Change change) {
      myChange = change;
    }

    public void actionPerformed(AnActionEvent e) {
      askAndMove(myProject, new Change[]{myChange});
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
      ShowDiffAction.showDiffForChange(changes, indexInSelection, myProject, new DiffToolbarActionsFactory());
    }
    else {
      ShowDiffAction.showDiffForChange(new Change[]{leadSelection}, 0, myProject, new DiffToolbarActionsFactory());
    }
  }

  private class DiffToolbarActionsFactory implements ShowDiffAction.AdditionalToolbarActionsFactory {
    public List<? extends AnAction> createActions(Change change) {
      return Arrays.asList(new MoveAction(change), new ToggleChangeAction(change));
    }
  }

  private void rebuildList() {
    final ChangeListManager manager = ChangeListManager.getInstance(myProject);
    myChangeListsMap.clear();
    for (Change change : myAllChanges) {
      myChangeListsMap.put(change, manager.getChangeList(change));
    }

    myViewer.setChangesToDisplay(getCurrentDisplayedChanges());
  }

  private class ChangeListChooser extends JPanel {
    public ChangeListChooser(List<? extends ChangeList> lists) {
      super(new BorderLayout());
      final JComboBox chooser = new JComboBox(lists.toArray());
      chooser.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final LocalChangeList l = ((LocalChangeList)value);
          append(l.getName(),
                 l.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });

      chooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            setSelectedList((LocalChangeList)chooser.getSelectedItem());
          }
        }
      });

      chooser.setSelectedItem(mySelectedChangeList);
      chooser.setEditable(false);
      chooser.setEnabled(lists.size() > 1);
      add(chooser, BorderLayout.EAST);

      JLabel label = new JLabel(VcsBundle.message("commit.dialog.changelist.label"));
      label.setDisplayedMnemonic('l');
      label.setLabelFor(chooser);
      add(label, BorderLayout.CENTER);
    }
  }

  private void setSelectedList(final ChangeList list) {
    mySelectedChangeList = list;
    rebuildList();
    myDispatcher.getMulticaster().selectedListChanged();
  }

  private JComponent createToolbar() {
    DefaultActionGroup toolBarGroup = new DefaultActionGroup();
    final ShowDiffAction diffAction = new ShowDiffAction() {
      public void actionPerformed(AnActionEvent e) {
        showDiff();
      }
    };

    final MoveChangesToAnotherListAction moveAction = new MoveChangesToAnotherListAction() {
      public void actionPerformed(AnActionEvent e) {
        super.actionPerformed(e);
        rebuildList();
      }
    };

    diffAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      myViewer);

    moveAction.registerCustomShortcutSet(CommonShortcuts.getMove(), myViewer);

    ToggleShowDirectoriesAction directoriesAction = new ToggleShowDirectoriesAction();
    directoriesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK)),
      myViewer);

    toolBarGroup.add(diffAction);
    toolBarGroup.add(moveAction);
    toolBarGroup.add(directoriesAction);

    if (myAdditionalToolbarActions != null) {
      toolBarGroup.addSeparator();
      toolBarGroup.add(myAdditionalToolbarActions);
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolBarGroup, true).getComponent();
  }

  public List<Change> getCurrentDisplayedChanges() {
    return filterBySelectedChangeList(myAllChanges);
  }

  public List<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(myViewer.getIncludedChanges());
  }

  public ChangeList getSelectedChangeList() {
    return mySelectedChangeList;
  }

  private List<Change> filterBySelectedChangeList(final Collection<Change> changes) {
    List<Change> filtered = new ArrayList<Change>();
    for (Change change : changes) {
      if (getList(change) == mySelectedChangeList) {
        filtered.add(change);
      }
    }
    return filtered;
  }

  private ChangeList getList(final Change change) {
    return myChangeListsMap.get(change);
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
}
