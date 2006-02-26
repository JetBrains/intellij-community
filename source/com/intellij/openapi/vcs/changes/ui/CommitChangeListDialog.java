package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.DataProvider;
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
  private Collection<Change> myChanges;
  private Collection<Change> myIncludedChanges;

  private List<RefreshableOnComponent> myAdditionalComponents = new ArrayList<RefreshableOnComponent>();
  private List<CheckinHandler> myHandlers = new ArrayList<CheckinHandler>();
  private String myActionName;

  public CommitChangeListDialog(Project project, ChangeList list, final List<Change> changes) {
    super(project, true);
    myProject = project;
    myChanges = list.getChanges();
    myIncludedChanges = new ArrayList<Change>(changes);
    myActionName = "Commit Changes"; // TODO: should be customizable;

    myAdditionalOptionsPanel = new JPanel();
    myCommitMessageArea = new CommitMessage();

    final DefaultListModel listModel = new DefaultListModel();
    myChangesList = new JList(listModel);

    for (Change change : myChanges) {
      listModel.addElement(change);
    }

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

    myChangesList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1) {
          toggleSelection();
        }
      }
    });

    setOKButtonText("Commit");

    setTitle(myActionName);

    restoreState();

    init();
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
        Runnable checkinAction = new Runnable() {
          public void run() {
            try {
              Map<AbstractVcs, List<Change>> changesByVcs = new HashMap<AbstractVcs, List<Change>>();
              for (Change change : myIncludedChanges) {
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

                  vcsExceptions
                    .addAll(environment.commit(paths.toArray(new FilePath[paths.size()]), myProject, getCommitMessage()));
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
              commitCompleted(vcsExceptions, VcsConfiguration.getInstance(myProject), myHandlers);
            }
          }
        };
        ProgressManager.getInstance().runProcessWithProgressSynchronously(checkinAction, myActionName, true, myProject);
      }
    };

    AbstractVcsHelper.getInstance(myProject).optimizeImportsAndReformatCode(getVirtualFiles(),
                                                                            VcsConfiguration.getInstance(myProject), checkinAction, true);
  }

  private static void commitCompleted(final List<VcsException> allExceptions,
                                      VcsConfiguration config,
                                      final List<CheckinHandler> checkinHandlers) {
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
        if (myIncludedChanges.contains(value)) {
          myIncludedChanges.remove(value);
        }
        else {
          myIncludedChanges.add((Change)value);
        }
      }
    }
    myChangesList.repaint();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    myChangesList.setVisibleRowCount(10);
    myRootPane = new Splitter(true);
    myRootPane.setHonorComponentsMinimumSize(true);
    final JScrollPane pane = new JScrollPane(myChangesList);
    pane.setPreferredSize(new Dimension(400, 400));
    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(pane);
    topPanel.setBorder(IdeBorderFactory.createTitledHeaderBorder("Changed Files"));
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

  public List<AbstractVcs> getAffectedVcses() {
    Set<AbstractVcs> result = new HashSet<AbstractVcs>();
    for (Change change : myChanges) {
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
    for (Change change : myChanges) {
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
    for (Change change: myIncludedChanges) {
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
    for (Change change: myIncludedChanges) {
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
    for (Change change : myIncludedChanges) {
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

  @Nullable
  public Object getData(String dataId) {
    if (CheckinProjectPanel.PANEL.equals(dataId)) {
      return this;
    }

    return null;
  }
}
