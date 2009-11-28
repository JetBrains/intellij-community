/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

abstract class AbstractShowPropertiesDiffAction extends AnAction implements DumbAware {
  protected abstract DataKey<Change[]> getChangesKey();
  @Nullable
  protected abstract SVNRevision getBeforeRevisionValue(final Change change, final SvnVcs vcs) throws SVNException;
  @Nullable
  protected abstract SVNRevision getAfterRevisionValue(final Change change, final SvnVcs vcs) throws SVNException;

  @Override
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Change[] changes = e.getData(getChangesKey());

    final Presentation presentation = e.getPresentation();
    presentation.setVisible(VcsDataKeys.CHANGES.getData(dataContext) != null);
    presentation.setEnabled(enabled(project, changes));
  }

  private boolean enabled(final Project project, final Change[] changes) {
    final boolean noChange = (project == null) || (changes == null) || (changes.length != 1);
    if (noChange) {
      return false;
    } else {
      final Change change = changes[0];

      final ContentRevision revision = (change.getBeforeRevision() != null) ? change.getBeforeRevision() : change.getAfterRevision();
      if ((revision == null) || (! (revision.getRevisionNumber() instanceof SvnRevisionNumber))) {
        return false;
      }

      return checkVcs(project, change);
    }
  }

  protected boolean checkVcs(final Project project, final Change change) {
    final VirtualFile virtualFile = ChangesUtil.getFilePath(change).getVirtualFile();
    if (virtualFile == null) {
      return false;
    }
    final AbstractVcs vcs = ChangesUtil.getVcsForFile(virtualFile, project);
    return (vcs != null) && SvnVcs.VCS_NAME.equals(vcs.getName());
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Change[] changes = e.getData(getChangesKey());

    if (! enabled(project, changes)) {
      return;
    }

    final Change change = changes[0];

    final CalculateAndShow worker = new CalculateAndShow(project, change, e.getPresentation().getText());
    ProgressManager.getInstance().run(worker);
  }

  private class CalculateAndShow extends Task.Backgroundable {
    private final Change myChange;
    private String myBeforeContent;
    private String myAfterContent;
    private SVNRevision myBeforeRevisionValue;
    private SVNRevision myAfterRevision;
    private SVNException myException;
    private final String myErrorTitle;

    private CalculateAndShow(@Nullable final Project project, final Change change, final String errorTitle) {
      super(project, SvnBundle.message("fetching.properties.contents.progress.title"), true, Backgroundable.DEAF);
      myChange = change;
      myErrorTitle = errorTitle;
    }

    public void run(@NotNull final ProgressIndicator indicator) {
      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      final SVNWCClient client = vcs.createWCClient();

      try {
        myBeforeRevisionValue = getBeforeRevisionValue(myChange, vcs);
        myAfterRevision = getAfterRevisionValue(myChange, vcs);

        myBeforeContent = getPropertyList(myChange.getBeforeRevision(), myBeforeRevisionValue, client);
        indicator.checkCanceled();
        // gets exactly WORKING revision property
        myAfterContent = getPropertyList(myChange.getAfterRevision(), myAfterRevision, client);
      }
      catch (SVNException exc) {
        myException = exc;
      }

      // since sometimes called from modal dialog (commit changes dialog)
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myException != null) {
            Messages.showErrorDialog(myException.getMessage(), myErrorTitle);
            return;
          }
          if (myBeforeContent != null && myAfterContent != null && myBeforeRevisionValue != null && myAfterRevision != null) {
            final SimpleDiffRequest diffRequest = new SimpleDiffRequest(myProject, getDiffWindowTitle(myChange));
            if (compareRevisions(myBeforeRevisionValue, myAfterRevision) >= 0) {
              // before ahead
              diffRequest.setContents(new SimpleContent(myAfterContent), new SimpleContent(myBeforeContent));
              diffRequest.setContentTitles(revisionToString(myAfterRevision), revisionToString(myBeforeRevisionValue));
            } else {
              diffRequest.setContents(new SimpleContent(myBeforeContent), new SimpleContent(myAfterContent));
              diffRequest.setContentTitles(revisionToString(myBeforeRevisionValue), revisionToString(myAfterRevision));
            }
            DiffManager.getInstance().getDiffTool().show(diffRequest);
          }
        }
      });
    }
  }

  private String getDiffWindowTitle(final Change change) {
    if (change.isMoved() || change.isRenamed()) {
      final FilePath beforeFilePath = ChangesUtil.getBeforePath(change);
      final FilePath afterFilePath = ChangesUtil.getAfterPath(change);

      final String beforePath = beforeFilePath == null ? "" : beforeFilePath.getIOFile().getAbsolutePath();
      final String afterPath = afterFilePath == null ? "" : afterFilePath.getIOFile().getAbsolutePath();
      return SvnBundle.message("action.Subversion.properties.difference.diff.for.move.title", beforePath, afterPath);
    } else {
      return SvnBundle.message("action.Subversion.properties.difference.diff.title", ChangesUtil.getFilePath(change).getIOFile().getAbsolutePath());
    }
  }

  private int compareRevisions(@NonNls final SVNRevision revision1, @NonNls final SVNRevision revision2) {
    if (revision1.equals(revision2)) {
      return 0;
    }
    // working(local) ahead of head
    if (SVNRevision.WORKING.equals(revision1)) {
      return 1;
    }
    if (SVNRevision.WORKING.equals(revision2)) {
      return -1;
    }
    if (SVNRevision.HEAD.equals(revision1)) {
      return 1;
    }
    if (SVNRevision.HEAD.equals(revision2)) {
      return -1;
    }
    return revision1.getNumber() > revision2.getNumber() ? 1 : -1;
  }

  private String revisionToString(final SVNRevision revision) {
    if (revision == null) {
      return "not exists";
    }
    return revision.toString();
  }

  private final static String ourPropertiesDelimiter = "\n";

  private String getPropertyList(final ContentRevision contentRevision, final SVNRevision revision, final SVNWCClient client) throws SVNException {
    if (contentRevision == null) {
      return "";
    }

    final StringBuilder sb = new StringBuilder();
    final List<SVNPropertyData> lines = new ArrayList<SVNPropertyData>();

    final File ioFile = contentRevision.getFile().getIOFile();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(SvnBundle.message("show.properties.diff.progress.text.revision.information", revision.toString()));
    }

    final ISVNPropertyHandler propertyHandler = new ISVNPropertyHandler() {
      public void handleProperty(final File path, final SVNPropertyData property) throws SVNException {
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(SvnBundle.message("show.properties.diff.progress.text2.property.information", property.getName()));
        }
        lines.add(property);
      }

      public void handleProperty(final SVNURL url, final SVNPropertyData property) throws SVNException {
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(SvnBundle.message("show.properties.diff.progress.text2.property.information", property.getName()));
        }
        lines.add(property);
      }

      public void handleProperty(final long revision, final SVNPropertyData property) throws SVNException {
        // revision properties here
      }
    };

    if (contentRevision instanceof SvnRepositoryContentRevision) {
      final SvnRepositoryContentRevision svnRevision = (SvnRepositoryContentRevision) contentRevision;
      client.doGetProperty(SVNURL.parseURIEncoded(svnRevision.getFullPath()), null, revision, revision, SVNDepth.EMPTY, propertyHandler);
    } else {
      client.doGetProperty(ioFile, null, revision, revision, SVNDepth.EMPTY, propertyHandler, null);
    }

    Collections.sort(lines, new Comparator<SVNPropertyData>() {
      public int compare(final SVNPropertyData o1, final SVNPropertyData o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (SVNPropertyData line : lines) {
      addPropertyPresentation(line, sb);
    }

    return sb.toString();
  }

  private void addPropertyPresentation(final SVNPropertyData property, final StringBuilder sb) {
    if (sb.length() != 0) {
      sb.append(ourPropertiesDelimiter);
    }
    sb.append(property.getName()).append("=").append((property.getValue() == null) ? "" : SVNPropertyValue.getPropertyAsString(property.getValue()));
  }

}
