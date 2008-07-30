package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class CommitChangeListDialog extends DialogWrapper implements CheckinProjectPanel, TypeSafeDataProvider {
  private CommitMessage myCommitMessageArea;
  private Splitter mySplitter;
  private JPanel myAdditionalOptionsPanel;

  private ChangesBrowser myBrowser;
  private ChangesBrowserExtender myBrowserExtender;

  private CommitLegendPanel myLegend;

  private final List<RefreshableOnComponent> myAdditionalComponents = new ArrayList<RefreshableOnComponent>();
  private final List<CheckinHandler> myHandlers = new ArrayList<CheckinHandler>();
  private String myActionName;
  private final Project myProject;
  private final List<CommitExecutor> myExecutors;
  private final Alarm myOKButtonUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private String myLastKnownComment = "";
  private boolean myAllOfDefaultChangeListChangesIncluded;
  @NonNls private static final String SPLITTER_PROPORTION_OPTION = "CommitChangeListDialog.SPLITTER_PROPORTION";
  private final Action[] myExecutorActions;
  private final boolean myShowVcsCommit;
  private LocalChangeList myLastSelectedChangeList = null;
  private Map<AbstractVcs, JPanel> myPerVcsOptionsPanels = new HashMap<AbstractVcs, JPanel>();

  @Nullable
  private final AbstractVcs myVcs;
  private final boolean myIsAlien;
  private boolean myDisposed = false;

  private static class MyUpdateButtonsRunnable implements Runnable {
    private CommitChangeListDialog myDialog;

    private MyUpdateButtonsRunnable(final CommitChangeListDialog dialog) {
      myDialog = dialog;
    }

    public void cancel() {
      myDialog = null;
    }

    public void run() {
      if (myDialog != null) {
        myDialog.updateButtons();
        myDialog.updateLegend();
      }
    }
  }

  private MyUpdateButtonsRunnable myUpdateButtonsRunnable = new MyUpdateButtonsRunnable(this);

  private static void commit(Project project, final List<Change> changes, final LocalChangeList initialSelection,
                             final List<CommitExecutor> executors, boolean showVcsCommit, final String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final LocalChangeList defaultList = manager.getDefaultChangeList();
    final ArrayList<LocalChangeList> changeLists = new ArrayList<LocalChangeList>(manager.getChangeLists());
    new CommitChangeListDialog(project, changes, initialSelection, executors, showVcsCommit, defaultList, changeLists, null, false, comment).show();
  }

  public static void commitPaths(final Project project, Collection<FilePath> paths, final LocalChangeList initialSelection,
                                 @Nullable final CommitExecutor executor, final String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    final Collection<Change> changes = new HashSet<Change>();
    for (FilePath path : paths) {
      changes.addAll(manager.getChangesIn(path));
    }

    commitChanges(project, changes, initialSelection, executor, comment);
  }

  public static void commitChanges(final Project project, final Collection<Change> changes, final LocalChangeList initialSelection,
                                   final CommitExecutor executor, final String comment) {
    final ChangeListManager manager = ChangeListManager.getInstance(project);
    if (executor == null) {
      commitChanges(project, changes, initialSelection, manager.getRegisteredExecutors(), true, comment);
    }
    else {
      commitChanges(project, changes, initialSelection, Collections.singletonList(executor), false, comment);
    }
  }

  public static void commitChanges(final Project project, final Collection<Change> changes, final LocalChangeList initialSelection,
                                   final List<CommitExecutor> executors, final boolean showVcsCommit, final String comment) {
    if (changes.isEmpty()) {
      Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text") ,
                                 VcsBundle.message("commit.dialog.no.changes.detected.title"));
      return;
    }

    commit(project, new ArrayList<Change>(changes), initialSelection, executors, showVcsCommit, comment);
  }

  public static void commitAlienChanges(final Project project, final List<Change> changes, final AbstractVcs vcs,
                                        final String changelistName, final String comment) {
    final LocalChangeList lcl = new AlienLocalChangeList(changes, changelistName);
    new CommitChangeListDialog(project, changes, null, null, true, AlienLocalChangeList.DEFAULT_ALIEN, Collections.singletonList(lcl), vcs,
                               true, comment).show();
  }

  private CommitChangeListDialog(final Project project,
                                 final List<Change> changes,
                                 final LocalChangeList initialSelection,
                                 final List<CommitExecutor> executors,
                                 final boolean showVcsCommit, final LocalChangeList defaultChangeList,
                                 final List<LocalChangeList> changeLists, final AbstractVcs singleVcs, final boolean isAlien,
                                 final String comment) {
    super(project, true);
    myProject = project;
    myExecutors = executors;
    myShowVcsCommit = showVcsCommit;
    myVcs = singleVcs;

    if (!myShowVcsCommit && ((myExecutors == null) || myExecutors.size() == 0)) {
      throw new IllegalArgumentException("nothing found to execute commit with");
    }

    myAllOfDefaultChangeListChangesIncluded = changes.containsAll(defaultChangeList.getChanges());

    myIsAlien = isAlien;
    if (isAlien) {
      AlienChangeListBrowser browser = new AlienChangeListBrowser(project, changeLists, changes, initialSelection, true, true, singleVcs);
      myBrowser = browser;
      myBrowserExtender = browser;
    } else {
      MultipleChangeListBrowser browser = new MultipleChangeListBrowser(project, changeLists, changes, initialSelection, true, true);
      myBrowser = browser;
      myBrowserExtender = browser.getExtender();
    }

    myBrowserExtender.addToolbarActions(this);

    myBrowserExtender.addSelectedListChangeListener(new SelectedListChangeListener() {
      public void selectedListChanged() {
        updateComment();
        updateVcsOptionsVisibility();
      }
    });
    myBrowser.setDiffExtendUIFactory(new ShowDiffAction.DiffExtendUIFactory() {
      public List<? extends AnAction> createActions(final Change change) {
        return myBrowser.createDiffActions(change);
      }

      @Nullable
      public JComponent createBottomComponent() {
        return new DiffCommitMessageEditor(CommitChangeListDialog.this);
      }
    });

    myCommitMessageArea = new CommitMessage();
    myCommitMessageArea.init();

    if (comment != null) {
      setCommitMessage(comment);
      myLastKnownComment = comment;
      myLastSelectedChangeList = initialSelection;
    } else {
      setCommitMessage(VcsConfiguration.getInstance(project).LAST_COMMIT_MESSAGE);
      updateComment();

      String messageFromVcs = getInitialMessageFromVcs();
      if (messageFromVcs != null) {
        myCommitMessageArea.setText(messageFromVcs);
      }
    }

    myActionName = VcsBundle.message("commit.dialog.title");

    myAdditionalOptionsPanel = new JPanel();

    myAdditionalOptionsPanel.setLayout(new BorderLayout());
    Box optionsBox = Box.createVerticalBox();

    boolean hasVcsOptions = false;
    Box vcsCommitOptions = Box.createVerticalBox();
    final List<AbstractVcs> vcses = getAffectedVcses();
    for (AbstractVcs vcs : vcses) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment != null) {
        final RefreshableOnComponent options = checkinEnvironment.createAdditionalOptionsPanel(this);
        if (options != null) {
          JPanel vcsOptions = new JPanel(new BorderLayout());
          vcsOptions.add(options.getComponent(), BorderLayout.CENTER);
          vcsOptions.add(SeparatorFactory.createSeparator(vcs.getDisplayName(), null), BorderLayout.NORTH);
          vcsCommitOptions.add(vcsOptions);
          myPerVcsOptionsPanels.put(vcs, vcsOptions);
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

    final String actionName = getCommitActionName();
    final String borderTitleName = actionName.replace("_", "");
    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue());
      beforeBox.add(SeparatorFactory.createSeparator(VcsBundle.message("border.standard.checkin.options.group", borderTitleName), null), 0);
      optionsBox.add(beforeBox);
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue());
      afterBox.add(SeparatorFactory.createSeparator(VcsBundle.message("border.standard.after.checkin.options.group", borderTitleName), null), 0);
      optionsBox.add(afterBox);
    }

    if (hasVcsOptions || beforeVisible || afterVisible) {
      optionsBox.add(Box.createVerticalGlue());
      myAdditionalOptionsPanel.add(optionsBox, BorderLayout.NORTH);
    }

    setOKButtonText(actionName);

    if (myShowVcsCommit) {
      setTitle(myActionName);
    }
    else {
      setTitle(trimEllipsis(myExecutors.get(0).getActionText()));
    }

    restoreState();

    if (myExecutors != null) {
      myExecutorActions = new Action[myExecutors.size()];

      for (int i = 0; i < myExecutors.size(); i++) {
        final CommitExecutor commitExecutor = myExecutors.get(i);
        myExecutorActions[i] = new CommitExecutorAction(commitExecutor, i == 0 && !myShowVcsCommit);
      }
    } else {
      myExecutorActions = null;
    }

    init();
    updateButtons();
    updateVcsOptionsVisibility();
    myCommitMessageArea.requestFocusInMessage();
  }

  private void updateVcsOptionsVisibility() {
    final List<AbstractVcs> affectedVcses = getAffectedVcses(myBrowser.getSelectedChangeList().getChanges());
    for(Map.Entry<AbstractVcs, JPanel> entry: myPerVcsOptionsPanels.entrySet()) {
      entry.getValue().setVisible(affectedVcses.contains(entry.getKey()));
    }
  }

  protected Action[] createActions() {
    Action[] result;
    final int executorsSize = (myExecutors == null) ? 0 : myExecutors.size();

    if (myShowVcsCommit) {
      result = new Action[2 + executorsSize];
      result[0] = getOKAction();
      if (myExecutors != null) {
        System.arraycopy(myExecutorActions, 0, result, 1, myExecutorActions.length);
      }
    }
    else {
      result = new Action[1 + executorsSize];
      if (myExecutors != null) {
        System.arraycopy(myExecutorActions, 0, result, 0, myExecutorActions.length);
      }
    }
    result[result.length - 1] = getCancelAction();
    return result;
  }

  private void execute(final CommitExecutor commitExecutor) {
    if (!saveDialogState()) return;
    final CommitSession session = commitExecutor.createCommitSession();
    boolean isOK = true;
    if (SessionDialog.createConfigurationUI(session, getIncludedChanges(), getCommitMessage())!= null) {
      DialogWrapper sessionDialog = new SessionDialog(commitExecutor.getActionText(),
                                                      getProject(),
                                                      session,
                                                      getIncludedChanges(),
                                                      getCommitMessage());
      sessionDialog.show();
      isOK = sessionDialog.isOK();
    }
    if (isOK) {
      runBeforeCommitHandlers(new Runnable() {
        public void run() {
          try {
            final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
              public void run() {
                session.execute(getIncludedChanges(), getCommitMessage());
              }
            }, commitExecutor.getActionText(), true, getProject());

            if (completed) {
              for (CheckinHandler handler : myHandlers) {
                handler.checkinSuccessful();
              }

              clearDefaulListComment();
              close(OK_EXIT_CODE);
            }
            else {
              session.executionCanceled();
            }
          }
          catch (Throwable e) {
            Messages.showErrorDialog(VcsBundle.message("error.executing.commit", commitExecutor.getActionText(), e.getLocalizedMessage()),
                                     commitExecutor.getActionText());

            for (CheckinHandler handler : myHandlers) {
              handler.checkinFailed(Arrays.asList(new VcsException(e)));
            }
          }
        }
      }, commitExecutor);


    }
    else {
      session.executionCanceled();
    }
  }

  private void clearDefaulListComment() {
    final LocalChangeList list = (LocalChangeList) myBrowser.getSelectedChangeList();
    if (isDefaultList(list)) {
      list.setComment("");
    }
  }

  @Nullable
  private String getInitialMessageFromVcs() {
    final List<Change> list = getIncludedChanges();
    final Ref<String> result = new Ref<String>();
    ChangesUtil.processChangesByVcs(myProject, list, new ChangesUtil.PerVcsProcessor<Change>() {
      public void process(final AbstractVcs vcs, final List<Change> items) {
        if (result.isNull()) {
          CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
          if (checkinEnvironment != null) {
            final Collection<FilePath> paths = ChangesUtil.getPaths(items);
            String defaultMessage = checkinEnvironment.getDefaultMessageFor(paths.toArray(new FilePath[paths.size()]));
            if (defaultMessage != null) {
              result.set(defaultMessage);
            }
          }
        }
      }
    });
    return result.get();
  }

  private void saveCommentIntoChangeList() {
    if (myLastSelectedChangeList != null) {
      final String actualCommentText = myCommitMessageArea.getComment();
      if ((! Comparing.equal(myLastSelectedChangeList.getComment(), actualCommentText)) &&
          (! Comparing.equal(actualCommentText, VcsConfiguration.getInstance(myProject).LAST_COMMIT_MESSAGE))) {
        myLastSelectedChangeList.setComment(actualCommentText);
      }
    }
  }

  private boolean isDefaultList(final LocalChangeList list) {
    return VcsBundle.message("changes.default.changlist.name").equals(list.getName());
  }

  private void updateComment() {
    final LocalChangeList list = (LocalChangeList) myBrowser.getSelectedChangeList();
    if (list == null || list == myLastSelectedChangeList) {
      return;
    } else if (myLastSelectedChangeList != null) {
      saveCommentIntoChangeList();
    }
    myLastSelectedChangeList = list;

    String listComment = list.getComment();
    if (StringUtil.isEmptyOrSpaces(listComment)) {
      final String listTitle = list.getName();
      if (! isDefaultList(list)) {
        listComment = listTitle;
      }
      else {
        // use last know comment; it is already stored in list
        listComment = myLastKnownComment;
      }
    }

    myCommitMessageArea.setText(listComment);
  }


  @Override
  public void dispose() {
    myBrowser.dispose();
    Disposer.dispose(myCommitMessageArea);
    Disposer.dispose(myOKButtonUpdateAlarm);
    myUpdateButtonsRunnable.cancel();
    super.dispose();
    PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION_OPTION, String.valueOf(mySplitter.getProportion()));
  }

  public String getCommitActionName() {
    String name = null;
    for (AbstractVcs vcs : getAffectedVcses()) {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (name == null && checkinEnvironment != null) {
        name = checkinEnvironment.getCheckinOperationName();
      }
      else {
        name = VcsBundle.message("commit.dialog.default.commit.operation.name");
      }
    }
    return name != null ? name : VcsBundle.message("commit.dialog.default.commit.operation.name");
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

  private void runBeforeCommitHandlers(final Runnable okAction, final CommitExecutor executor) {
    Runnable proceedRunnable = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();

        for (CheckinHandler handler : myHandlers) {
          final CheckinHandler.ReturnResult result = handler.beforeCheckin(executor);
          if (result == CheckinHandler.ReturnResult.COMMIT) continue;
          if (result == CheckinHandler.ReturnResult.CANCEL) return;

          if (result == CheckinHandler.ReturnResult.CLOSE_WINDOW) {
            final ChangeList changeList = myBrowser.getSelectedChangeList();
            CommitHelper.moveToFailedList(changeList,
                             getCommitMessage(),
                             getIncludedChanges(),
                             VcsBundle.message("commit.dialog.rejected.commit.template", changeList.getName()),
                             myProject);
            doCancelAction();
            return;
          }
        }

        okAction.run();
      }
    };

    for(CheckinHandler handler: myHandlers) {
      if (handler instanceof CheckinMetaHandler) {
        ((CheckinMetaHandler) handler).runCheckinHandlers(proceedRunnable);
        return;
      }
    }
    proceedRunnable.run();
  }

  protected void doOKAction() {
    if (!saveDialogState()) return;

    try {
      runBeforeCommitHandlers(new Runnable() {
        public void run() {
          CommitChangeListDialog.super.doOKAction();
          doCommit();
        }
      }, null);

      clearDefaulListComment();
    }
    catch (InputException ex) {
      ex.show();
    }
  }

  private boolean saveDialogState() {
    if (!checkComment()) {
      return false;
    }

    saveCommentIntoChangeList();
    VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    try {
      saveState();
    }
    catch(InputException ex) {
      ex.show();
      return false;
    }
    return true;
  }

  @Override
  public void doCancelAction() {
    saveCommentIntoChangeList();
    //VcsConfiguration.getInstance(myProject).saveCommitMessage(getCommitMessage());
    super.doCancelAction();
  }

  private void doCommit() {
    final CommitHelper helper = new CommitHelper(
      myProject,
      myBrowser.getSelectedChangeList(),
      getIncludedChanges(),
      myActionName,
      getCommitMessage(),
      myHandlers,
      myAllOfDefaultChangeListChangesIncluded, false);

    if (myIsAlien) {
      helper.doAlienCommit(myVcs);
    } else {
      helper.doCommit();
    }
  }
  
  @Nullable
  protected JComponent createCenterPanel() {
    JPanel rootPane = new JPanel(new BorderLayout());

    mySplitter = new Splitter(true);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstComponent(myBrowser);
    mySplitter.setSecondComponent(myCommitMessageArea);
    mySplitter.setProportion(calcSplitterProportion());
    rootPane.add(mySplitter, BorderLayout.CENTER);

    JComponent browserHeader = myBrowser.getHeaderPanel();
    myBrowser.remove(browserHeader);
    rootPane.add(browserHeader, BorderLayout.NORTH);

    JPanel infoPanel = new JPanel(new BorderLayout());
    myLegend = new CommitLegendPanel();
    infoPanel.add(myLegend.getComponent(), BorderLayout.NORTH);
    infoPanel.add(myAdditionalOptionsPanel, BorderLayout.CENTER);
    rootPane.add(infoPanel, BorderLayout.EAST);
    infoPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 0));

    return rootPane;
  }

  private static float calcSplitterProportion() {
    try {
      final String s = PropertiesComponent.getInstance().getValue(SPLITTER_PROPORTION_OPTION);
      return s != null ? Float.valueOf(s).floatValue() : 0.5f;
    }
    catch (NumberFormatException e) {
      return 0.5f;
    }
  }

  public List<AbstractVcs> getAffectedVcses() {
    if (! myShowVcsCommit) {
      return Collections.emptyList();
    }
    return myBrowserExtender.getAffectedVcses();
  }

  private List<AbstractVcs> getAffectedVcses(final Collection<Change> changes) {
    Set<AbstractVcs> result = new HashSet<AbstractVcs>();
    for (Change change : changes) {
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(change, myProject);
      if (vcs != null) {
        result.add(vcs);
      }
    }
    return new ArrayList<AbstractVcs>(result);
  }

  public Collection<VirtualFile> getRoots() {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    for (Change change : myBrowser.getCurrentDisplayedChanges()) {
      final FilePath filePath = ChangesUtil.getFilePath(change);
      VirtualFile root = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(filePath);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  public JComponent getComponent() {
    return mySplitter;
  }

  public boolean hasDiffs() {
    return !getIncludedChanges().isEmpty();
  }

  public void addSelectionChangeListener(SelectionChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  public void removeSelectionChangeListener(SelectionChangeListener listener) {
    throw new UnsupportedOperationException();
  }

  public Collection<VirtualFile> getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (Change change: getIncludedChanges()) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final VirtualFile vFile = path.getVirtualFile();
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return result;
  }

  public Collection<File> getFiles() {
    List<File> result = new ArrayList<File>();
    for (Change change: getIncludedChanges()) {
      final FilePath path = ChangesUtil.getFilePath(change);
      final File file = path.getIOFile();
      result.add(file);
    }

    return result;
  }

  public Project getProject() {
    return myProject;
  }

  public void setCommitMessage(final String currentDescription) {
    setCommitMessageText(currentDescription);
    myCommitMessageArea.requestFocusInMessage();
  }

  private void setCommitMessageText(final String currentDescription) {
    myLastKnownComment = currentDescription;
    myCommitMessageArea.setText(currentDescription);
  }

  public String getCommitMessage() {
    return myCommitMessageArea.getComment();
  }

  public void refresh() {
    ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
      public void run() {
        myBrowser.rebuildList();
        for (RefreshableOnComponent component : myAdditionalComponents) {
          component.refresh();
        }
      }
    }, false, true, "commit dialog");   // title not shown for silently
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

  private void updateButtons() {
    if (myDisposed) return;
    setOKActionEnabled(hasDiffs());
    if (myExecutorActions != null) {
      for (Action executorAction : myExecutorActions) {
        executorAction.setEnabled(hasDiffs());
      }
    }
    myOKButtonUpdateAlarm.cancelAllRequests();
    myOKButtonUpdateAlarm.addRequest(myUpdateButtonsRunnable, 300, ModalityState.stateForComponent(myBrowser));
  }

  private void updateLegend() {
    myLegend.update(myBrowser.getCurrentDisplayedChanges(), myBrowserExtender.getCurrentIncludedChanges());
  }

  @NotNull
  private List<Change> getIncludedChanges() {
    return myBrowserExtender.getCurrentIncludedChanges();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "CommitChangelistDialog";
  }
  
  public JComponent getPreferredFocusedComponent() {
    return myCommitMessageArea.getTextField();
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key == CheckinProjectPanel.PANEL_KEY) {
      sink.put(CheckinProjectPanel.PANEL_KEY, this);
    }
    else {
      myBrowser.calcData(key, sink);
    }
  }

  static String trimEllipsis(final String title) {
    if (title.endsWith("...")) {
      return title.substring(0, title.length() - 3);
    }
    else {
      return title;
    }
  }

  private class CommitExecutorAction extends AbstractAction {
    private final CommitExecutor myCommitExecutor;

    public CommitExecutorAction(final CommitExecutor commitExecutor, final boolean isDefault) {
      super(commitExecutor.getActionText());
      myCommitExecutor = commitExecutor;
      if (isDefault) {
        putValue(DEFAULT_ACTION, Boolean.TRUE);
      }
    }

    public void actionPerformed(ActionEvent e) {
      execute(myCommitExecutor);
    }
  }

  private static class DiffCommitMessageEditor extends JPanel implements Disposable {
    private CommitChangeListDialog myCommitDialog;
    private final JTextArea myArea = new JTextArea();

    public DiffCommitMessageEditor(final CommitChangeListDialog dialog) {
      super(new BorderLayout());
      myArea.setText(dialog.getCommitMessage());
      myArea.setLineWrap(true);      
      myArea.setWrapStyleWord(true);      
      JScrollPane scrollPane = new JScrollPane(myArea);
      setBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("diff.commit.message.title")));
      add(scrollPane, BorderLayout.CENTER);
      myCommitDialog = dialog;
    }

    public void dispose() {
      if (myCommitDialog != null) {
        myCommitDialog.setCommitMessageText(myArea.getText());
        myCommitDialog = null;
      }
    }

    public Dimension getPreferredSize() {
      // we don't want to be squeezed to one line
      return new Dimension(400, 120);
    }
  }
}
