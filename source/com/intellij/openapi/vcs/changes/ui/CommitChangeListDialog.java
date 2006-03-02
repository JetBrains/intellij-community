package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.VcsOperation;
import com.intellij.openapi.vcs.ui.CheckinDialog;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class CommitChangeListDialog extends DialogWrapper implements CheckinProjectPanel, DataProvider {
  private CommitMessage myCommitMessageArea;
  private JList myChangesList;
  private Splitter myRootPane;
  private JPanel myAdditionalOptionsPanel;

  private Project myProject;

  private ChangeList mySelectedChangeList;
  private Collection<Change> myAllChanges;
  private Collection<Change> myIncludedChanges;

  private List<RefreshableOnComponent> myAdditionalComponents = new ArrayList<RefreshableOnComponent>();
  private List<CheckinHandler> myHandlers = new ArrayList<CheckinHandler>();
  private String myActionName;
  private List<ChangeList> myChangeLists;

  private static void commit(Project project, List<ChangeList> list, final List<Change> changes) {
    new CommitChangeListDialog(project, list, changes).show();
  }

  public static void commitFile(Project project, VirtualFile file) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Change change = manager.getChange(file);
    if (change == null) {
      Messages.showWarningDialog(project, "No changes for file: '" + file.getPresentableUrl() + "'.", "No Changes Detected");
      return;
    }

    commit(project, Arrays.asList(manager.getChangeList(change)), Arrays.asList(change));
  }

  public static void commitFiles(final Project project, Collection<VirtualFile> directoriesOrFiles) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Collection<Change> changes = new HashSet<Change>();
    for (VirtualFile file : directoriesOrFiles) {
      changes.addAll(manager.getChangesIn(file));
    }

    commitChanges(project, changes);
  }

  public static void commitPaths(final Project project, Collection<FilePath> paths) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Collection<Change> changes = new HashSet<Change>();
    for (FilePath path : paths) {
      changes.addAll(manager.getChangesIn(path));
    }

    commitChanges(project, changes);
  }

  public static void commitChanges(final Project project, final Collection<Change> changes) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);

    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, "No changes detected." , "No Changes Detected");
      return;
    }

    Set<ChangeList> lists = new THashSet<ChangeList>();
    for (Change change : changes) {
      lists.add(manager.getChangeList(change));
    }

    commit(project, new ArrayList<ChangeList>(lists), new ArrayList<Change>(changes));
  }

  private CommitChangeListDialog(final Project project, List<ChangeList> changeLists, final List<Change> changes) {
    super(project, true);
    myProject = project;
    myChangeLists = changeLists;
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

    myIncludedChanges = new ArrayList<Change>(changes);
    myActionName = "Commit Changes"; // TODO: should be customizable?

    myAdditionalOptionsPanel = new JPanel();
    myCommitMessageArea = new CommitMessage();

    myChangesList = new JList(new DefaultListModel());

    setSelectedList(initalListSelection);

    setCommitMessage(CheckinDialog.getInitialMessage(getPaths(), project));

    myChangesList.setCellRenderer(new MyListCellRenderer());

    myAdditionalOptionsPanel.setLayout(new BorderLayout());
    Box optionsBox = Box.createVerticalBox();

    Box vcsCommitOptions = Box.createVerticalBox();
    boolean hasVcsOptions = false;
    final List<AbstractVcs> vcses = getAffectedVcses();
    for (AbstractVcs vcs : vcses) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment != null) {
        final RefreshableOnComponent options = checkinEnvironment.createAdditionalOptionsPanelForCheckinProject(this);
        if (options != null) {
          JPanel vcsOptions = new JPanel(new BorderLayout());
          vcsOptions.add(options.getComponent());
          vcsOptions.setBorder(IdeBorderFactory.createTitledHeaderBorder(vcs.getDisplayName()));
          vcsCommitOptions.add(vcsOptions);
          myAdditionalComponents.add(options);
          hasVcsOptions = true;
        }
      }
    }

    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue());
      optionsBox.add(vcsCommitOptions);
    }

    boolean beforeVisible = false;
    boolean afterVisible = false;
    Box beforeBox = Box.createVerticalBox();
    Box afterBox = Box.createVerticalBox();
    final List<CheckinHandlerFactory> handlerFactories = ProjectLevelVcsManager.getInstance(project).getRegisteredCheckinHandlerFactories();
    for (CheckinHandlerFactory factory : handlerFactories) {
      final CheckinHandler handler = factory.createHandler(this);
      myHandlers.add(handler);
      final RefreshableOnComponent beforePanel = handler.getBeforeCheckinConfigurationPanel();
      if (beforePanel != null) {
        beforeBox.add(beforePanel.getComponent());
        beforeVisible = true;
        myAdditionalComponents.add(beforePanel);
      }

      final RefreshableOnComponent afterPanel = handler.getAfterCheckinConfigurationPanel();
      if (afterPanel != null) {
        afterBox.add(afterPanel.getComponent());
        afterVisible = true;
        myAdditionalComponents.add(afterPanel);
      }
    }

    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue());
      beforeBox.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("border.standard.checkin.options.group")));
      optionsBox.add(beforeBox);
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue());
      afterBox.setBorder(IdeBorderFactory.createTitledHeaderBorder(VcsBundle.message("border.standard.after.checkin.options.group")));
      optionsBox.add(afterBox);
    }

    if (hasVcsOptions || beforeVisible || afterVisible) {
      optionsBox.add(Box.createVerticalGlue());
      myAdditionalOptionsPanel.add(optionsBox, BorderLayout.NORTH);
    }

    myChangesList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        toggleSelection();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);

    final int checkboxWidth = new JCheckBox().getPreferredSize().width;

    myChangesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final int idx = myChangesList.locationToIndex(e.getPoint());
        if (idx >= 0) {
          final Rectangle baseRect = myChangesList.getCellBounds(idx, idx);
          baseRect.setSize(checkboxWidth, baseRect.height);
          if (baseRect.contains(e.getPoint())) {
            toggleChange((Change)myChangesList.getModel().getElementAt(idx));
          }
          else if (e.getClickCount() == 2) {
            ShowDiffAction.showDiffForChange((Change)myChangesList.getModel().getElementAt(idx), project);
          }
        }
      }
    });

    setOKButtonText("Commit");

    setTitle(myActionName);

    restoreState();

    init();
  }

  private void rebuildList() {
    final DefaultListModel listModel = (DefaultListModel)myChangesList.getModel();
    listModel.removeAllElements();
    for (Change change : getCurrentDisplayedChanges()) {
      listModel.addElement(change);
    }
  }

  private boolean checkComment() {
    if (VcsConfiguration.getInstance(myProject).FORCE_NON_EMPTY_COMMENT && (getCommitMessage().length() == 0)) {
      int requestForCheckin = Messages.showYesNoDialog(VcsBundle.message("confirmation.text.check.in.with.empty.comment"),
                                                       VcsBundle.message("confirmation.title.check.in.with.empty.comment"),
                                                       Messages.getWarningIcon());
      return requestForCheckin == OK_EXIT_CODE;
    }
    else {
      return true;
    }
  }

  public boolean runBeforeCommitHandlers() {
    for (CheckinHandler handler : myHandlers) {
      final CheckinHandler.ReturnResult result = handler.beforeCheckin();
      if (result == CheckinHandler.ReturnResult.COMMIT) continue;
      if (result == CheckinHandler.ReturnResult.CANCEL) return false;
      if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
        doCancelAction();
        return false;
      }
    }

    return true;
  }

  protected void doOKAction() {
    if (!checkComment()) {
      return;
    }

    VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    try {
      saveState();

      if (!runBeforeCommitHandlers()) {
        return;
      }

      doCommit();

      super.doOKAction();
    }
    catch (InputException ex) {
      ex.show();
    }
  }

  private void doCommit() {
    final Runnable checkinAction = new Runnable() {
      public void run() {
        final List<VcsException> vcsExceptions = new ArrayList<VcsException>();
        final List<Change> changesFailedToCommit = new ArrayList<Change>();

        Runnable checkinAction = new Runnable() {
          public void run() {
            try {
              Map<AbstractVcs, List<Change>> changesByVcs = new HashMap<AbstractVcs, List<Change>>();
              for (Change change : getCurrentIncludedChanges()) {
                final AbstractVcs vcs = getVcsForChange(change);
                if (vcs != null) {
                  List<Change> vcsChanges = changesByVcs.get(vcs);
                  if (vcsChanges == null) {
                    vcsChanges = new ArrayList<Change>();
                    changesByVcs.put(vcs, vcsChanges);
                  }
                  vcsChanges.add(change);
                }
              }

              final List<FilePath> pathsToRefresh = new ArrayList<FilePath>();

              for (AbstractVcs vcs : changesByVcs.keySet()) {
                final CheckinEnvironment environment = vcs.getCheckinEnvironment();
                if (environment != null) {
                  final List<Change> vcsChanges = changesByVcs.get(vcs);
                  List<FilePath> paths = new ArrayList<FilePath>();
                  for (Change change : vcsChanges) {
                    paths.add(getFilePath(change));
                  }

                  pathsToRefresh.addAll(paths);

                  final List<VcsException> exceptions = environment.commit(paths.toArray(new FilePath[paths.size()]), myProject, getCommitMessage());
                  if (exceptions.size() > 0) {
                    vcsExceptions.addAll(exceptions);
                    changesFailedToCommit.addAll(vcsChanges);
                  }
                }
              }

              final LvcsAction lvcsAction = LocalVcs.getInstance(myProject).startAction(myActionName, "", true);
              VirtualFileManager.getInstance().refresh(true, new Runnable() {
                public void run() {
                  lvcsAction.finish();
                  FileStatusManager.getInstance(myProject).fileStatusesChanged();
                  for (FilePath path : pathsToRefresh) {
                    VcsDirtyScopeManager.getInstance(myProject).fileDirty(path);
                  }
                }
              });
              AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, myActionName);
            }
            finally {
              commitCompleted(vcsExceptions, changesFailedToCommit, VcsConfiguration.getInstance(myProject), myHandlers, getCommitMessage());
            }
          }
        };

        ProgressManager.getInstance().runProcessWithProgressSynchronously(checkinAction, myActionName, true, myProject);
      }
    };

    AbstractVcsHelper.getInstance(myProject).optimizeImportsAndReformatCode(getVirtualFiles(),
                                                                            VcsConfiguration.getInstance(myProject), checkinAction, true);
  }

  private void commitCompleted(final List<VcsException> allExceptions,
                               final List<Change> failedChanges,
                               VcsConfiguration config,
                               final List<CheckinHandler> checkinHandlers,
                               String commitMessage) {
    final List<VcsException> errors = collectErrors(allExceptions);
    final int errorsSize = errors.size();
    final int warningsSize = allExceptions.size() - errorsSize;

    if (errorsSize == 0) {
      for (CheckinHandler handler : checkinHandlers) {
        handler.checkinSuccessful();
      }
    }
    else {
      for (CheckinHandler handler : checkinHandlers) {
        handler.checkinFailed(errors);
      }

      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      final ChangeList failedList = changeListManager.addChangeList("Failed commit: " + commitMessage);

      changeListManager.moveChangesTo(failedList, failedChanges.toArray(new Change[failedChanges.size()]));
    }

    config.ERROR_OCCURED = errorsSize > 0;


    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (errorsSize > 0 && warningsSize > 0) {
          Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors.and.warnings"),
                                   VcsBundle.message("message.title.commit"));
        }
        else if (errorsSize > 0) {
          Messages.showErrorDialog(VcsBundle.message("message.text.commit.failed.with.errors"), VcsBundle.message("message.title.commit"));
        }
        else if (warningsSize > 0) {
          Messages
            .showErrorDialog(VcsBundle.message("message.text.commit.finished.with.warnings"), VcsBundle.message("message.title.commit"));
        }

      }
    }, ModalityState.NON_MMODAL);

  }

  private static List<VcsException> collectErrors(final List<VcsException> vcsExceptions) {
    final ArrayList<VcsException> result = new ArrayList<VcsException>();
    for (VcsException vcsException : vcsExceptions) {
      if (!vcsException.isWarning()) {
        result.add(vcsException);
      }
    }
    return result;
  }

  @SuppressWarnings({"SuspiciousMethodCalls"})
  private void toggleSelection() {
    final Object[] values = myChangesList.getSelectedValues();
    if (values != null) {
      for (Object value : values) {
        toggleChange((Change)value);
      }
    }
    myChangesList.repaint();
  }

  private void toggleChange(Change value) {
    if (myIncludedChanges.contains(value)) {
      myIncludedChanges.remove(value);
    }
    else {
      myIncludedChanges.add(value);
    }
    myChangesList.repaint();
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

      JLabel label = new JLabel("Change list: ");
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
    final ShowDiffAction diffAction = new ShowDiffAction();
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


  @Nullable
  protected JComponent createCenterPanel() {
    myChangesList.setVisibleRowCount(10);
    myRootPane = new Splitter(true);
    myRootPane.setHonorComponentsMinimumSize(true);
    final JScrollPane pane = new JScrollPane(myChangesList);
    pane.setPreferredSize(new Dimension(400, 400));

    JPanel topPanel = new JPanel(new BorderLayout());

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(pane);
    listPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder("Changed Files"));
    topPanel.add(listPanel, BorderLayout.CENTER);

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.add(new ChangeListChooser(myChangeLists), BorderLayout.EAST);
    headerPanel.add(createToolbar(), BorderLayout.WEST);
    topPanel.add(headerPanel, BorderLayout.NORTH);

    myRootPane.setFirstComponent(topPanel);

    JPanel bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(myAdditionalOptionsPanel, BorderLayout.EAST);
    bottomPanel.add(myCommitMessageArea, BorderLayout.CENTER);

    myRootPane.setSecondComponent(bottomPanel);
    myRootPane.setProportion(1);
    return myRootPane;
  }

  private static FilePath getFilePath(final Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (revision == null) revision = change.getBeforeRevision();

    return revision.getFile();
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
          final FilePath path = getFilePath(change);
          setIcon(path.getFileType().getIcon());
          append(path.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor(change), null));
          append(" (" + path.getIOFile().getParentFile().getPath() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        private Color getColor(final Change change) {
          final FilePath path = getFilePath(change);
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

  private Collection<Change> getCurrentDisplayedChanges() {
    return filterBySelectedChangeList(myAllChanges);
  }

  private Collection<Change> getCurrentIncludedChanges() {
    return filterBySelectedChangeList(myIncludedChanges);
  }

  private Collection<Change> filterBySelectedChangeList(final Collection<Change> changes) {
    List<Change> filtered = new ArrayList<Change>();
    final ChangeListManager manager = ChangeListManager.getInstance(myProject);
    for (Change change : changes) {
      if (manager.getChangeList(change) == mySelectedChangeList) {
        filtered.add(change);
      }
    }
    return filtered;
  }

  public List<AbstractVcs> getAffectedVcses() {
    Set<AbstractVcs> result = new HashSet<AbstractVcs>();
    for (Change change : myAllChanges) {
      final AbstractVcs vcs = getVcsForChange(change);
      if (vcs != null) {
        result.add(vcs);
      }
    }
    return new ArrayList<AbstractVcs>(result);
  }

  private AbstractVcs getVcsForChange(Change change) {
    final FilePath filePath = getFilePath(change);
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile root = VcsDirtyScope.getRootFor(fileIndex, filePath);
    if (root != null) {
      return vcsManager.getVcsFor(root);
    }

    return null;
  }

  public Collection<VirtualFile> getRoots() {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (Change change : getCurrentDisplayedChanges()) {
      final FilePath filePath = getFilePath(change);
      VirtualFile root = VcsDirtyScope.getRootFor(fileIndex, filePath);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  public JComponent getComponent() {
    return myRootPane;
  }

  public boolean hasDiffs() {
    return true;
  }

  public void addSelectionChangeListener(SelectionChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  public void removeSelectionChangeListener(SelectionChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  public Collection<VirtualFile> getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Change change: getCurrentIncludedChanges()) {
      final FilePath path = getFilePath(change);
      final VirtualFile vFile = path.getVirtualFile();
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return result;
  }

  public Collection<File> getFiles() {
    List<File> result = new ArrayList<File>();
    for (Change change: getCurrentIncludedChanges()) {
      final FilePath path = getFilePath(change);
      final File file = path.getIOFile();
      if (file != null) {
        result.add(file);
      }
    }

    return result;
  }

  private FilePath[] getPaths() {
    List<FilePath> result = new ArrayList<FilePath>();
    for (Change change : getCurrentIncludedChanges()) {
      result.add(getFilePath(change));
    }
    return result.toArray(new FilePath[result.size()]);
  }

  public Project getProject() {
    return myProject;
  }

  public List<VcsOperation> getCheckinOperations(CheckinEnvironment checkinEnvironment) {
    throw new UnsupportedOperationException();
  }

  public void setCommitMessage(final String currentDescription) {
    myCommitMessageArea.setText(currentDescription);
    myCommitMessageArea.requestFocusInMessage();
  }

  public String getCommitMessage() {
    return myCommitMessageArea.getComment();
  }

  public void refresh() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.refresh();
    }
  }

  public void saveState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.saveState();
    }
  }

  public void restoreState() {
    for (RefreshableOnComponent component : myAdditionalComponents) {
      component.restoreState();
    }
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "CommitChangelistDialog";
  }
  
  public JComponent getPreferredFocusedComponent() {
    if (VcsConfiguration.getInstance(myProject).PUT_FOCUS_INTO_COMMENT) {
      return myCommitMessageArea.getTextField();
    }
    else {
      return myChangesList;
    }
  }

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

  @Nullable
  public Object getData(String dataId) {
    if (CheckinProjectPanel.PANEL.equals(dataId)) {
      return this;
    }
    else if (DataConstants.CHANGES.equals(dataId)) {
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
}
