package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
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
    myView = new ChangesListView(project);
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
    DefaultActionGroup group = new DefaultActionGroup();
    final RefreshAction action = new RefreshAction();
    JPanel panel = new JPanel(new BorderLayout());
    action.registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);
    group.add(action);
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
      for (ChangeList list : myChangeLists) {
        list.updateChangesIn(scope, changes);
      }
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
            myView.updateModel();
          }
        }, 100);
      }
    });
  }

  public List<ChangeList> getChangeLists() {
    synchronized (myChangeLists) {
      return myChangeLists;
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
}
