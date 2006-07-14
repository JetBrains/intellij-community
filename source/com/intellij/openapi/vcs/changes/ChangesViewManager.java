/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.07.2006
 * Time: 15:29:25
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.util.Alarm;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class ChangesViewManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangesViewManager");

  private static final String TOOLWINDOW_ID = VcsBundle.message("changes.toolwindow.name");

  private boolean SHOW_FLATTEN_MODE = true;

  private ChangesListView myView;
  private JLabel myProgressLabel;
  private final Project myProject;

  private Alarm myRepaintAlarm;

  private boolean myDisposed = false;

  private ChangeListListener myListener = new MyChangeListListener();

  @NonNls private static final String ATT_FLATTENED_VIEW = "flattened_view";

  static ChangesViewManager getInstance(Project project) {
    return project.getComponent(ChangesViewManager.class);
  }

  public ChangesViewManager(Project project) {
    myProject = project;
    myView = new ChangesListView(project);
    Disposer.register(project, myView);
    myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
  }

  public void projectOpened() {
    ChangeListManager.getInstance(myProject).addChangeListListener(myListener);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) {
          toolWindowManager.registerToolWindow(TOOLWINDOW_ID, createChangeViewComponent(), ToolWindowAnchor.BOTTOM);
        }
      }
    });
  }

  public void projectClosed() {
    ChangeListManager.getInstance(myProject).removeChangeListListener(myListener);
    myDisposed = true;
    myRepaintAlarm.cancelAllRequests();
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(TOOLWINDOW_ID);
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private JComponent createChangeViewComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    DefaultActionGroup modelActionsGroup = new DefaultActionGroup();

    RefreshAction refreshAction = new RefreshAction();
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);

    AddChangeListAction newChangeListAction = new AddChangeListAction();
    newChangeListAction.registerCustomShortcutSet(CommonShortcuts.getNew(), panel);

    final RemoveChangeListAction removeChangeListAction = new RemoveChangeListAction();
    removeChangeListAction.registerCustomShortcutSet(CommonShortcuts.DELETE, panel);

    final ShowDiffAction diffAction = new ShowDiffAction();
    diffAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                                                                                      SystemInfo.isMac
                                                                                      ? KeyEvent.META_DOWN_MASK
                                                                                      : KeyEvent.CTRL_DOWN_MASK)),
                                         panel);

    final MoveChangesToAnotherListAction toAnotherListAction = new MoveChangesToAnotherListAction();
    toAnotherListAction.registerCustomShortcutSet(CommonShortcuts.getMove(), panel);

    final SetDefaultChangeListAction setDefaultChangeListAction = new SetDefaultChangeListAction();
    final RenameChangeListAction renameAction = new RenameChangeListAction();
    renameAction.registerCustomShortcutSet(CommonShortcuts.getRename(), panel);

    final CommitAction commitAction = new CommitAction();
    final RollbackAction rollbackAction = new RollbackAction();

    modelActionsGroup.add(refreshAction);
    modelActionsGroup.add(commitAction);

    /*
    for (CommitExecutor executor : myExecutors) {
      modelActionsGroup.add(new CommitUsingExecutorAction(executor));
    }
    */

    modelActionsGroup.add(rollbackAction);
    modelActionsGroup.add(newChangeListAction);
    modelActionsGroup.add(removeChangeListAction);
    modelActionsGroup.add(setDefaultChangeListAction);
    modelActionsGroup.add(toAnotherListAction);
    modelActionsGroup.add(diffAction);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(createToolbarComponent(modelActionsGroup), BorderLayout.WEST);

    DefaultActionGroup visualActionsGroup = new DefaultActionGroup();
    final Expander expander = new Expander();
    visualActionsGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(expander));
    visualActionsGroup.add(CommonActionsManager.getInstance().createExpandAllAction(expander));

    ToggleShowFlattenAction showFlattenAction = new ToggleShowFlattenAction();
    showFlattenAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                                                                                             SystemInfo.isMac
                                                                                             ? KeyEvent.META_DOWN_MASK
                                                                                             : KeyEvent.CTRL_DOWN_MASK)),
                                                panel);
    visualActionsGroup.add(showFlattenAction);
    toolbarPanel.add(createToolbarComponent(visualActionsGroup), BorderLayout.CENTER);


    DefaultActionGroup menuGroup = new DefaultActionGroup();
    menuGroup.add(refreshAction);

    menuGroup.add(commitAction);
    /*
    for (CommitExecutor executor : myExecutors) {
      menuGroup.add(new CommitUsingExecutorAction(executor));
    }
    */

    menuGroup.add(rollbackAction);
    menuGroup.add(newChangeListAction);
    menuGroup.add(removeChangeListAction);
    menuGroup.add(setDefaultChangeListAction);
    menuGroup.add(renameAction);
    menuGroup.add(toAnotherListAction);
    menuGroup.add(diffAction);
    menuGroup.addSeparator();
    menuGroup.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    menuGroup.add(new DeleteAction());
    menuGroup.add(new ScheduleForAdditionAction());
    menuGroup.add(new ScheduleForRemovalAction());
    menuGroup.add(new RollbackDeletionAction());
    menuGroup.addSeparator();
    menuGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));

    myView.setMenuActions(menuGroup);

    myView.setShowFlatten(SHOW_FLATTEN_MODE);

    myProgressLabel = new JLabel();

    panel.add(toolbarPanel, BorderLayout.WEST);
    panel.add(new JScrollPane(myView), BorderLayout.CENTER);
    panel.add(myProgressLabel, BorderLayout.SOUTH);

    myView.installDndSupport(ChangeListManagerImpl.getInstanceImpl(myProject));
    return panel;
  }

  private static JComponent createToolbarComponent(final DefaultActionGroup group) {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW, group, false).getComponent();
  }

  void updateProgressText(final String text) {
    if (myProgressLabel != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myProgressLabel.setText(text);
        }
      });
    }
  }

  void scheduleRefresh() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    myRepaintAlarm.cancelAllRequests();
    myRepaintAlarm.addRequest(new Runnable() {
      public void run() {
        refreshView();
      }
    }, 100, ModalityState.NON_MMODAL);
  }

  void refreshView() {
    if (myDisposed || ApplicationManager.getApplication().isUnitTestMode()) return;
    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    myView.updateModel(changeListManager.getChangeListsCopy(),
                       changeListManager.getUnversionedFiles(),
                       changeListManager.getDeletedFiles());
  }

  public void readExternal(Element element) throws InvalidDataException {
    SHOW_FLATTEN_MODE = Boolean.valueOf(element.getAttributeValue(ATT_FLATTENED_VIEW)).booleanValue();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATT_FLATTENED_VIEW, String.valueOf(SHOW_FLATTEN_MODE));
  }

  private class MyChangeListListener implements ChangeListListener {

    public void changeListAdded(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRemoved(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListChanged(ChangeList list) {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    public void changeListRenamed(ChangeList list, String oldName) {
      scheduleRefresh();
    }

    public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
      scheduleRefresh();
    }

    public void defaultListChanged(ChangeList newDefaultList) {
      scheduleRefresh();
    }
  }

  private class Expander implements TreeExpander {
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 2);
      TreeUtil.expand(myView, 1);
    }

    public boolean canCollapse() {
      return true;
    }
  }

  public class ToggleShowFlattenAction extends ToggleAction {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !SHOW_FLATTEN_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_FLATTEN_MODE = !state;
      myView.setShowFlatten(SHOW_FLATTEN_MODE);
      refreshView();
    }
  }

  public class RefreshAction extends AnAction {
    public RefreshAction() {
      super(VcsBundle.message("changes.action.refresh.text"),
            VcsBundle.message("changes.action.refresh.description"),
            IconLoader.getIcon("/actions/sync.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }

  public class AddChangeListAction extends AnAction {
    public AddChangeListAction() {
      super(VcsBundle.message("changes.action.new.changelist.text"),
            VcsBundle.message("changes.action.new.changelist.description"),
            IconLoader.getIcon("/actions/include.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      NewChangelistDialog dlg = new NewChangelistDialog(myProject);
      dlg.show();
      if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        String name = dlg.getName();
        if (name.length() == 0) {
          name = getUniqueName();
        }

        ChangeListManager.getInstance(myProject).addChangeList(name, dlg.getDescription());
      }
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private String getUniqueName() {
      int unnamedcount = 0;
      for (ChangeList list : ChangeListManagerImpl.getInstanceImpl(myProject).getChangeListsCopy()) {
        if (list.getName().startsWith("Unnamed")) {
          unnamedcount++;
        }
      }

      return unnamedcount == 0 ? "Unnamed" : "Unnamed (" + unnamedcount + ")";
    }
  }

  public class RenameChangeListAction extends AnAction {

    public RenameChangeListAction() {
      super(VcsBundle.message("changes.action.rename.text"),
            VcsBundle.message("changes.action.rename.description"), null);
    }

    public void update(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(lists != null && lists.length == 1 && lists[0] instanceof LocalChangeList &&
                                     !((LocalChangeList) lists [0]).isReadOnly());
    }

    public void actionPerformed(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      assert lists != null;
      final LocalChangeList list = ChangeListManager.getInstance(myProject).findChangeList(lists[0].getName());
      if (list != null) {
        new EditChangelistDialog(myProject, list).show();
      }
      else {
        LOG.assertTrue(false, "Cannot find changelist " + lists [0].getName());
      }
    }
  }

  public class SetDefaultChangeListAction extends AnAction {
    public SetDefaultChangeListAction() {
      super(VcsBundle.message("changes.action.setdefaultchangelist.text"),
            VcsBundle.message("changes.action.setdefaultchangelist.description"), IconLoader.getIcon("/actions/submit1.png"));
    }


    public void update(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(lists != null && lists.length == 1 &&
                                     lists[0] instanceof LocalChangeList && !((LocalChangeList)lists[0]).isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      final ChangeList[] lists = ((ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS));
      assert lists != null;
      ChangeListManager.getInstance(myProject).setDefaultChangeList((LocalChangeList)lists[0]);
    }
  }

  public class CommitAction extends AnAction {
    public CommitAction() {
      super(VcsBundle.message("changes.action.commit.text"), VcsBundle.message("changes.action.commit.description"),
            IconLoader.getIcon("/actions/execute.png"));
    }

    public void update(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      e.getPresentation().setEnabled(ChangesUtil.getChangeListIfOnlyOne(myProject, changes) != null);
    }

    public void actionPerformed(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(myProject, changes);
      if (list == null) return;

      CommitChangeListDialog.commitChanges(myProject, Arrays.asList(changes), list, 
                                           ChangeListManager.getInstance(myProject).getRegisteredExecutors());
    }
  }

  public class RollbackAction extends AnAction {
    public RollbackAction() {
      super(VcsBundle.message("changes.action.rollback.text"), VcsBundle.message("changes.action.rollback.description"),
            IconLoader.getIcon("/actions/rollback.png"));
    }

    public void update(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      e.getPresentation().setEnabled(ChangesUtil.getChangeListIfOnlyOne(myProject, changes) != null);
    }

    public void actionPerformed(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(myProject, changes);
      if (list == null) return;

      RollbackChangesDialog.rollbackChanges(myProject, Arrays.asList(changes));
    }
  }

  public class ScheduleForAdditionAction extends AnAction {
    public ScheduleForAdditionAction() {
      super(VcsBundle.message("changes.action.add.text"), VcsBundle.message("changes.action.add.description"),
            IconLoader.getIcon("/actions/include.png"));
    }

    public void update(AnActionEvent e) {
      //noinspection unchecked
      List<VirtualFile> files = (List<VirtualFile>)e.getDataContext().getData(ChangesListView.UNVERSIONED_FILES_KEY);
      boolean enabled = files != null && !files.isEmpty();
      e.getPresentation().setEnabled(enabled);
      e.getPresentation().setVisible(enabled);
    }

    public void actionPerformed(AnActionEvent e) {
      //noinspection unchecked
      final List<VirtualFile> files = (List<VirtualFile>)e.getDataContext().getData(ChangesListView.UNVERSIONED_FILES_KEY);
      if (files == null) return;

      final ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      changeListManager.addUnversionedFiles(changeListManager.getDefaultChangeList(), files);
    }
  }

  public abstract class AbstractMissingFilesAction extends AnAction {

    protected AbstractMissingFilesAction(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    public void update(AnActionEvent e) {
      //noinspection unchecked
      List<File> files = (List<File>)e.getDataContext().getData(ChangesListView.MISSING_FILES_KEY);
      boolean enabled = files != null && !files.isEmpty();
      e.getPresentation().setEnabled(enabled);
      e.getPresentation().setVisible(enabled);
    }

    public void actionPerformed(AnActionEvent e) {
      //noinspection unchecked
      final List<File> files = (List<File>)e.getDataContext().getData(ChangesListView.MISSING_FILES_KEY);
      if (files == null) return;

      ChangesUtil.processIOFilesByVcs(myProject, files, new ChangesUtil.PerVcsProcessor<File>() {
        public void process(final AbstractVcs vcs, final List<File> items) {
          final ChangeProvider provider = vcs.getChangeProvider();
          if (provider != null) {
            processFiles(provider, files);
          }
        }
      });

      for (File file : files) {
        FilePath path = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file);
        VcsDirtyScopeManager.getInstance(myProject).fileDirty(path);
      }
      scheduleRefresh();
    }

    protected abstract void processFiles(final ChangeProvider provider, final List<File> files);
  }

  public class ScheduleForRemovalAction extends AbstractMissingFilesAction {
    public ScheduleForRemovalAction() {
      super(VcsBundle.message("changes.action.remove.text"), VcsBundle.message("changes.action.remove.description"),
            IconLoader.getIcon("/actions/exclude.png"));
    }

    protected void processFiles(final ChangeProvider provider, final List<File> files) {
      provider.scheduleMissingFileForDeletion(files);
    }
  }

  public class RollbackDeletionAction extends AbstractMissingFilesAction {
    public RollbackDeletionAction() {
      super(VcsBundle.message("changes.action.rollback.deletion.text"),
            VcsBundle.message("changes.action.rollback.deletion.description"),
            IconLoader.getIcon("/actions/rollback.png"));
    }

    protected void processFiles(final ChangeProvider provider, final List<File> files) {
      provider.rollbackMissingFileDeletion(files);
    }
  }

  public static class RemoveChangeListAction extends AnAction {
    public RemoveChangeListAction() {
      super(VcsBundle.message("changes.action.removechangelist.text"),
            VcsBundle.message("changes.action.removechangelist.description"),
            IconLoader.getIcon("/actions/exclude.png"));
    }

    public void update(AnActionEvent e) {
      Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(canRemoveChangeLists(project, lists));
    }

    private static boolean canRemoveChangeLists(final Project project, final ChangeList[] lists) {
      if (project == null || lists == null || lists.length == 0) return false;
      for(ChangeList changeList: lists) {
        if (!(changeList instanceof LocalChangeList)) return false;
        LocalChangeList localChangeList = (LocalChangeList) changeList;
        if (localChangeList.isReadOnly() || localChangeList.isDefault()) return false;
      }
      return true;
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
      final ChangeList[] lists = ((ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS));
      assert lists != null;
      int rc;
      if (lists.length == 1) {
        final LocalChangeList list = (LocalChangeList)lists[0];
        rc = list.getChanges().size() == 0 ? DialogWrapper.OK_EXIT_CODE :
                 Messages.showYesNoDialog(project,
                                          VcsBundle.message("changes.removechangelist.warning.text", list.getName()),
                                          VcsBundle.message("changes.removechangelist.warning.title"),
                                          Messages.getQuestionIcon());
      }
      else {
        rc = Messages.showYesNoDialog(project,
                                      VcsBundle.message("changes.removechangelist.multiple.warning.text", lists.length),
                                      VcsBundle.message("changes.removechangelist.warning.title"),
                                      Messages.getQuestionIcon());
      }

      if (rc == DialogWrapper.OK_EXIT_CODE) {
        for(ChangeList list: lists) {
          ChangeListManager.getInstance(project).removeChangeList((LocalChangeList) list);
        }
      }
    }
  }
}