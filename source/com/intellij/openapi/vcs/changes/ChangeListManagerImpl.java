package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManager implements ProjectComponent {
  private Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private static final String TOOLWINDOW_ID = "Changes";

  private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private Alarm myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myInitilized = false;
  private boolean myDisposed = false;

  private final List<ChangeList> myChangeLists = new ArrayList<ChangeList>();
  private ChangeList myDefaultChangelist;
  private ChangesListView myView;

  public ChangeListManagerImpl(final Project project, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myDefaultChangelist = new ChangeList("Default");
    myDefaultChangelist.setDefault(true);
    myView = new ChangesListView();
    myChangeLists.add(myDefaultChangelist);
  }

  public void projectOpened() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(myProject).registerToolWindow(TOOLWINDOW_ID, createChangeViewComponent(), ToolWindowAnchor.BOTTOM);
          myInitilized = true;
        }
      });
    }
  }

  public void projectClosed() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      myDisposed = true;
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(TOOLWINDOW_ID);
    }
  }

  @NonNls
  public String getComponentName() {
    return "ChangeListManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private JComponent createChangeViewComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    DefaultActionGroup group = new DefaultActionGroup();

    RefreshAction refreshAction = new RefreshAction();
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);

    AddChangeListAction newChangeListAction = new AddChangeListAction();
    newChangeListAction.registerCustomShortcutSet(CommonShortcuts.getNew(), panel);

    final RemoveChangeListAction removeChangeListAction = new RemoveChangeListAction();
    removeChangeListAction.registerCustomShortcutSet(CommonShortcuts.DELETE, panel);

    group.add(refreshAction);
    group.add(newChangeListAction);
    group.add(removeChangeListAction);
    group.add(new SetDefaultChangeListAction());
    group.add(new MoveChangesToAnotherListAction());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ChangeView", group, false);
    panel.add(toolbar.getComponent(), BorderLayout.WEST);
    panel.add(new JScrollPane(myView), BorderLayout.CENTER);
    return panel;
  }

  public void scheduleUpdate() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        if (myDisposed) return;
        if (!myInitilized) {
          scheduleUpdate();
          return;
        }

        final List<VcsDirtyScope> scopes = ((VcsDirtyScopeManagerImpl)VcsDirtyScopeManager.getInstance(myProject)).retreiveScopes();
        for (VcsDirtyScope scope : scopes) {
          final AbstractVcs vcs = myVcsManager.getVcsFor(scope.getScopeRoot());
          if (vcs != null) {
            final ChangeProvider changeProvider = vcs.getChangeProvider();
            if (changeProvider != null) {
              final Collection<Change> changes = changeProvider.getChanges(scope);
              List<Change> filteredChanges = new ArrayList<Change>();
              for (Change change : changes) {
                if (isUnder(change, scope)) {
                  filteredChanges.add(change);
                }
              }
              udpateUI(scope, filteredChanges);
            }
          }
        }
      }

      private boolean isUnder(final Change change, final VcsDirtyScope scope) {
        final ContentRevision before = change.getBeforeRevision();
        final ContentRevision after = change.getAfterRevision();
        return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
      }
    }, 300);
  }

  private void udpateUI(VcsDirtyScope scope, final Collection<Change> changes) {
    synchronized (myChangeLists) {
      Set<Change> allChanges = new HashSet<Change>(changes);
      for (ChangeList list : myChangeLists) {
        if (list == myDefaultChangelist) continue;
        list.updateChangesIn(scope, allChanges);
        allChanges.removeAll(list.getChanges());
      }
      myDefaultChangelist.updateChangesIn(scope, allChanges);
      scheduleRefresh();
    }
  }

  private void scheduleRefresh() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myRepaintAlarm.cancelAllRequests();
        myRepaintAlarm.addRequest(new Runnable() {
          public void run() {
            if (myDisposed) return;
            myView.updateModel(getChangeLists());
          }
        }, 100);
      }
    });
  }

  @NotNull
  public List<ChangeList> getChangeLists() {
    synchronized (myChangeLists) {
      return myChangeLists;
    }
  }

  public ChangeList addChangeList(String name) {
    synchronized (myChangeLists) {
      final ChangeList list = new ChangeList(name);
      myChangeLists.add(list);
      scheduleRefresh();
      return list;
    }
  }

  public void removeChangeList(ChangeList list) {
    synchronized (myChangeLists) {
      if (list.isDefault()) throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));

      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        myDefaultChangelist.addChange(change);
      }
      myChangeLists.remove(list);

      scheduleRefresh();
    }
  }

  public void setDefaultChangeList(ChangeList list) {
    synchronized (myChangeLists) {
      myDefaultChangelist.setDefault(false);
      list.setDefault(true);
      myDefaultChangelist = list;
      scheduleRefresh();
    }
  }

  public class RefreshAction extends AnAction {
    public RefreshAction() {
      super("Refresh", "Refresh VCS changes", IconLoader.getIcon("/actions/sync.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }

  public class AddChangeListAction extends AnAction {
    public AddChangeListAction() {
      super("New ChangeList", "Create new changelist", IconLoader.getIcon("/actions/include.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      String rc = Messages.showInputDialog(myProject, "Enter new changelist name", "New ChangeList", Messages.getQuestionIcon());
      if (rc != null) {
        if (rc.length() == 0) {
          rc = getUniqueName();
        }

        addChangeList(rc);
      }
    }

    private String getUniqueName() {
      int unnamedcount = 0;
      for (ChangeList list : getChangeLists()) {
        if (list.getDescription().startsWith("Unnamed")) {
          unnamedcount++;
        }
      }

      return unnamedcount == 0 ? "Unnamed" : "Unnamed (" + unnamedcount + ")";
    }
  }

  public class SetDefaultChangeListAction extends AnAction {
    public SetDefaultChangeListAction() {
      super("Set Default", "Set default changelist", IconLoader.getIcon("/actions/submit1.png"));
    }


    public void update(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(lists != null && lists.length == 1 && !lists[0].isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      setDefaultChangeList(((ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS))[0]);
    }
  }

  public class RemoveChangeListAction extends AnAction {
    public RemoveChangeListAction() {
      super("Remove Changelist", "Remove changelist and move all changes to default", IconLoader.getIcon("/actions/exclude.png"));
    }


    public void update(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(lists != null && lists.length == 1 && !lists[0].isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      final ChangeList list = ((ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS))[0];
      int rc = list.getChanges().size() == 0 ? DialogWrapper.OK_EXIT_CODE :
               Messages.showYesNoDialog(myProject,
                                        "Are you sure want to remove changelist '" + list.getDescription() + "'?\n" +
                                        "All changes will be moved to default changelist.",
                                        "Remove Change List",
                                        Messages.getQuestionIcon());

      if (rc == DialogWrapper.OK_EXIT_CODE) {
        removeChangeList(list);
      }
    }
  }

  public class MoveChangesToAnotherListAction extends AnAction {
    public MoveChangesToAnotherListAction() {
      super("Move to another list", "Move selected changes to another changelist", IconLoader.getIcon("/actions/fileStatus.png"));
    }

    public void update(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      e.getPresentation().setEnabled(changes != null && changes.length > 0);
    }

    public void actionPerformed(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      if (changes == null) return;

      ChangeListChooser chooser = new ChangeListChooser(myProject, getChangeLists(), null);
      chooser.show();
      ChangeList resultList = chooser.getSelectedList();
      if (resultList != null) {
        for (ChangeList list : getChangeLists()) {
          for (Change change : changes) {
            list.removeChange(change);
          }
        }

        for (Change change : changes) {
          resultList.addChange(change);
        }

        scheduleRefresh();
      }
    }
  }
}
