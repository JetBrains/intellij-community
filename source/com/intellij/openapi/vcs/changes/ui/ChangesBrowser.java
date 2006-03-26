package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangesBrowser extends JPanel implements DataProvider{
  private JList myChangesList;
  private ChangeList mySelectedChangeList;
  private Collection<Change> myAllChanges;
  private Collection<Change> myIncludedChanges;
  private FileStatusListener myFileStatusListener;
  private final Map<Change, ChangeList> myChangeListsMap = new HashMap<Change, ChangeList>();
  private Project myProject;

  public ChangesBrowser(final Project project, List<ChangeList> changeLists, final List<Change> changes) {
    super(new BorderLayout());

    myProject = project;
    final List<ChangeList> changeLists1;changeLists1 = changeLists;
    myAllChanges = new ArrayList<Change>();

    ChangeList initalListSelection = null;
    for (ChangeList list : changeLists) {
      myAllChanges.addAll(list.getChanges());
      if (list.isDefault()) {
        initalListSelection = list;
      }
    }

    if (initalListSelection == null) {
      initalListSelection = changeLists.get(0);
    }

    myIncludedChanges = new HashSet<Change>(changes);

    myChangesList = new JList(new DefaultListModel());

    new ListSpeedSearch(myChangesList) {
      protected String getElementText(Object element) {
        if (element instanceof Change) {
          return ChangesUtil.getFilePath((Change)element).getName();
        }
        return super.getElementText(element);
      }
    };

    setSelectedList(initalListSelection);

    myChangesList.setCellRenderer(new MyListCellRenderer());

    myChangesList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        toggleSelection();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);

    myChangesList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelection();
      }

    }, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_FOCUSED);

    myChangesList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelection();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_FOCUSED);

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;

    myChangesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int idx = myChangesList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myChangesList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            final Change currentSelection = (Change)myChangesList.getModel().getElementAt(idx);
            toggleChange(currentSelection);
          }
          else if (e.getClickCount() == 2) {
            showDiff();
          }
        }
      }
    });

    myFileStatusListener = new FileStatusListener() {
      public void fileStatusesChanged() {
        repaintData();
      }

      public void fileStatusChanged(VirtualFile virtualFile) {
        repaintData();
      }
    };

    FileStatusManager.getInstance(project).addFileStatusListener(myFileStatusListener);

    myChangesList.setVisibleRowCount(10);
    final JScrollPane pane = new JScrollPane(myChangesList);
    pane.setPreferredSize(new Dimension(400, 400));

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(pane);
    listPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("commit.dialog.changed.files.label")));
    add(listPanel, BorderLayout.CENTER);

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.add(new ChangeListChooser(changeLists1), BorderLayout.EAST);
    headerPanel.add(createToolbar(), BorderLayout.WEST);
    add(headerPanel, BorderLayout.NORTH);
  }

  public JComponent getContentComponent() {
    return this;
  }

  public void dispose() {
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
  }

  public Collection<Change> getAllChanges() {
    return myAllChanges;
  }

  public Object getData(final String dataId) {
    if (DataConstants.CHANGES.equals(dataId)) {
      return getSelectedChanges();
    }
    else if (DataConstants.CHANGE_LISTS.equals(dataId)) {
      return getSelectedChangeLists();
    }
    else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
      return getSelectedFiles();
    }

    return null;
  }

  private class MyListCellRenderer extends JPanel implements ListCellRenderer {
    private final ColoredListCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;

    public MyListCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myTextRenderer = new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          Change change = (Change)value;
          final FilePath path = ChangesUtil.getFilePath(change);
          setIcon(path.getFileType().getIcon());
          append(path.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor(change), null));
          append(" (" + path.getIOFile().getParentFile().getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        private Color getColor(final Change change) {
          final FilePath path = ChangesUtil.getFilePath(change);
          final VirtualFile vFile = path.getVirtualFile();
          if (vFile != null) {
            return FileStatusManager.getInstance(myProject).getStatus(vFile).getColor();
          }
          return FileStatus.DELETED.getColor();
        }
      };

      myCheckbox.setBackground(null);
      setBackground(null);

      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myTextRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      myCheckbox.setSelected(myIncludedChanges.contains(value));
      return this;
    }
  }

  private class MoveAction extends MoveChangesToAnotherListAction {
    private final Change myChange;


    public MoveAction(final Change change) {
      myChange = change;
    }

    public void actionPerformed(AnActionEvent e) {
      askAndMove(myProject, new Change[] {myChange});
    }
  }

  private class ToggleChangeAction extends CheckboxAction {
    private final Change myChange;

    public ToggleChangeAction(final Change change) {
      super(VcsBundle.message("commit.dialog.include.action.name"));
      myChange = change;
    }

    public boolean isSelected(AnActionEvent e) {
      return myIncludedChanges.contains(myChange);
    }

    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myIncludedChanges.add(myChange);
      }
      else {
        myIncludedChanges.remove(myChange);
      }
      repaintData();
    }
  }

  private void showDiff() {
    final int leadSelectionIndex = myChangesList.getLeadSelectionIndex();
    final Change leadSelection = (Change)myChangesList.getModel().getElementAt(leadSelectionIndex);

    Change[] changes = getSelectedChanges();

    if (changes.length < 2) {
      final Collection<Change> displayedChanges = getCurrentDisplayedChanges();
      changes = displayedChanges.toArray(new Change[displayedChanges.size()]);
    }

    int indexInSelection = Arrays.asList(changes).indexOf(leadSelection);
    if (indexInSelection >= 0) {
      ShowDiffAction.showDiffForChange(changes, indexInSelection, myProject, new DiffToolbarActionsFactory());
    }
    else {
      ShowDiffAction.showDiffForChange(new Change[] {leadSelection}, 0, myProject, new DiffToolbarActionsFactory());
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

    final DefaultListModel listModel = (DefaultListModel)myChangesList.getModel();
    listModel.removeAllElements();
    for (Change change : getCurrentDisplayedChanges()) {
      listModel.addElement(change);
    }
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void toggleSelection() {
    for (Change value : getSelectedChanges()) {
      toggleChange(value);
    }
    repaintData();
  }

  private void repaintData() {
    myChangesList.repaint();
  }

  private void includeSelection() {
    for (Change change : getSelectedChanges()) {
      myIncludedChanges.add(change);
    }
    repaintData();
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void excludeSelection() {
    for (Change change : getSelectedChanges()) {
      myIncludedChanges.remove(change);
    }
    repaintData();
  }

  private void toggleChange(Change value) {
    if (myIncludedChanges.contains(value)) {
      myIncludedChanges.remove(value);
    }
    else {
      myIncludedChanges.add(value);
    }
    repaintData();
  }

  private class ChangeListChooser extends JPanel {
    public ChangeListChooser(List<ChangeList> lists) {
      super(new BorderLayout());
      final JComboBox chooser = new JComboBox(lists.toArray());
      chooser.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final ChangeList l = ((ChangeList)value);
          append(l.getDescription(), l.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });

      chooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            setSelectedList((ChangeList)chooser.getSelectedItem());
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

    diffAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                                                                                      SystemInfo.isMac
                                                                                      ? KeyEvent.META_DOWN_MASK
                                                                                      : KeyEvent.CTRL_DOWN_MASK)), getRootPane());

    moveAction.registerCustomShortcutSet(CommonShortcuts.getMove(), getRootPane());

    toolBarGroup.add(diffAction);
    toolBarGroup.add(moveAction);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolBarGroup, true).getComponent();
  }

  public Collection<Change> getCurrentDisplayedChanges() {
    return filterBySelectedChangeList(myAllChanges);
  }

  public Collection<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(myIncludedChanges);
  }

  private Collection<Change> filterBySelectedChangeList(final Collection<Change> changes) {
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
    return myChangesList;
  }

  @NotNull
  private Change[] getSelectedChanges() {
    final Object[] o = myChangesList.getSelectedValues();
    final Change[] changes = new Change[o.length];
    for (int i = 0; i < changes.length; i++) {
      changes[i] = (Change)o[i];
    }
    return changes;
  }

  private ChangeList[] getSelectedChangeLists() {
    return new ChangeList[] {mySelectedChangeList};
  }

  private VirtualFile[] getSelectedFiles() {
    final Change[] changes = getSelectedChanges();
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
}
