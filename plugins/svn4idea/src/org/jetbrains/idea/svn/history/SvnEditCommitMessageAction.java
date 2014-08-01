/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/23/12
 * Time: 7:23 PM
 */
public class SvnEditCommitMessageAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
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

  public static void askAndEditRevision(final long number, final String oldComment, final SvnRepositoryLocation location, Project project, Consumer<String> listener, final boolean fromVersionControl) {
    final SvnEditCommitMessageDialog dialog = new SvnEditCommitMessageDialog(project, number, oldComment);
    dialog.show();
    if (DialogWrapper.OK_EXIT_CODE == dialog.getExitCode()) {
      final String edited = dialog.getMessage();
      if (edited.trim().equals(oldComment.trim())) return;
      ProgressManager.getInstance().run(new EditMessageTask(project, edited, location, number, listener, fromVersionControl));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    final ChangeList[] lists = VcsDataKeys.CHANGE_LISTS.getData(dc);
    final boolean enabled = lists != null && lists.length == 1 && lists[0] instanceof SvnChangeList;
    boolean visible = enabled;
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
    private final Consumer<String> myListener;
    private final boolean myFromVersionControl;
    private VcsException myException;
    private final SvnVcs myVcs;

    EditMessageTask(@Nullable Project project,
                    final String newMessage,
                    final SvnRepositoryLocation location,
                    final long number,
                    Consumer<String> listener,
                    boolean fromVersionControl) {
      super(project, "Edit Revision Comment");
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
      final SVNURL root;
      try {
        root = SvnUtil.getRepositoryRoot(myVcs, SVNURL.parseURIEncoded(url));
        if (root == null) {
          myException = new VcsException("Can not determine repository root for URL: " + url);
          return;
        }
        SvnTarget target = SvnTarget.fromURL(root);
        myVcs.getFactory(target).createPropertyClient()
          .setRevisionProperty(target, SvnPropertyKeys.LOG, SVNRevision.create(myNumber), PropertyValue.create(myNewMessage), false);
      }
      catch (SVNException e) {
        myException = new VcsException(e);
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        AbstractVcsHelper.getInstance(myProject).showError(myException, myTitle);
      } else {
        if (myListener != null) {
          myListener.consume(myNewMessage);
        }
        if (! myProject.isDefault()) {
          CommittedChangesCache.getInstance(myProject).commitMessageChanged(myVcs, myLocation, myNumber, myNewMessage);
        }
        if (myFromVersionControl) {
          VcsBalloonProblemNotifier.showOverVersionControlView(myProject, "Revision #" + myNumber + " comment " +
                                                                          "changed to:\n'" + myNewMessage + "'", MessageType.INFO);
        } else {
          VcsBalloonProblemNotifier.showOverChangesView(myProject, "Revision #" + myNumber + " comment " +
                                                                   "changed to:\n'" + myNewMessage + "'", MessageType.INFO);
        }
      }
    }
  }
}
