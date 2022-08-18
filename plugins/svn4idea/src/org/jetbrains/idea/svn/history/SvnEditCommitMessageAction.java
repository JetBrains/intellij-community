// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.properties.PropertyValue;

import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;
import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverVersionControlView;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnEditCommitMessageAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final ChangeList[] lists = VcsDataKeys.CHANGE_LISTS.getData(dc);
    final boolean enabled = lists != null && lists.length == 1 && lists[0] instanceof SvnChangeList;
    if (! enabled) return;
    final SvnChangeList svnList = (SvnChangeList) lists[0];
    Project project = CommonDataKeys.PROJECT.getData(dc);
    project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    final Consumer<String> listener = VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER.getData(dc);

    askAndEditRevision(svnList.getNumber(), svnList.getComment(), svnList.getLocation(), project, listener, false);
  }

  public static void askAndEditRevision(final long number, final String oldComment, final SvnRepositoryLocation location, Project project, Consumer<? super String> listener, final boolean fromVersionControl) {
    final SvnEditCommitMessageDialog dialog = new SvnEditCommitMessageDialog(project, number, oldComment);
    dialog.show();
    if (DialogWrapper.OK_EXIT_CODE == dialog.getExitCode()) {
      final String edited = dialog.getMessage();
      if (edited.trim().equals(oldComment.trim())) return;
      ProgressManager.getInstance().run(new EditMessageTask(project, edited, location, number, listener, fromVersionControl));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final ChangeList[] lists = VcsDataKeys.CHANGE_LISTS.getData(dc);
    final boolean enabled = lists != null && lists.length == 1 && lists[0] instanceof SvnChangeList;
    boolean visible;
    Project project = CommonDataKeys.PROJECT.getData(dc);
    if (project == null) {
      visible = VcsDataKeys.REMOTE_HISTORY_LOCATION.getData(dc) instanceof SvnRepositoryLocation;
    } else {
      visible = ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME);
    }
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  /*private boolean anyChangeUnderSvn(ChangeList[] lists) {
    for (ChangeList list : lists) {
      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        if (isSvn(change.getBeforeRevision()) || isSvn(change.getAfterRevision())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isSvn(ContentRevision cr) {
    return cr instanceof MarkerVcsContentRevision && SvnVcs.getKey().equals(((MarkerVcsContentRevision) cr).getVcsKey());
  }*/

  static class EditMessageTask extends Task.Backgroundable {
    private final String myNewMessage;
    private final SvnRepositoryLocation myLocation;
    private final long myNumber;
    private final Consumer<? super String> myListener;
    private final boolean myFromVersionControl;
    private VcsException myException;
    private final SvnVcs myVcs;

    EditMessageTask(@Nullable Project project,
                    final String newMessage,
                    final SvnRepositoryLocation location,
                    final long number,
                    Consumer<? super String> listener,
                    boolean fromVersionControl) {
      super(project, message("progress.title.edit.revision.comment"));
      myNewMessage = newMessage;
      myLocation = location;
      myNumber = number;
      myListener = listener;
      myFromVersionControl = fromVersionControl;
      myVcs = SvnVcs.getInstance(myProject);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      final String url = myLocation.getURL();
      final Url root;
      try {
        root = SvnUtil.getRepositoryRoot(myVcs, createUrl(url));
        if (root == null) {
          myException = new VcsException(message("error.can.not.find.repository.root.for.url", url));
          return;
        }
        Target target = Target.on(root);
        myVcs.getFactory(target).createPropertyClient()
          .setRevisionProperty(target, SvnPropertyKeys.LOG, Revision.of(myNumber), PropertyValue.create(myNewMessage), false);
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        AbstractVcsHelper.getInstance(myProject).showError(myException, message("tab.title.edit.revision.comment"));
      } else {
        if (myListener != null) {
          myListener.consume(myNewMessage);
        }
        if (!myProject.isDefault()) {
          CommittedChangesCache.getInstance(myProject).commitMessageChanged(myLocation, myNumber, myNewMessage);
        }

        String message = message("notification.content.revision.commit.message.changed.to", myNumber, myNewMessage);
        if (myFromVersionControl) {
          showOverVersionControlView(myProject, message, MessageType.INFO);
        }
        else {
          showOverChangesView(myProject, message, MessageType.INFO);
        }
      }
    }
  }
}
