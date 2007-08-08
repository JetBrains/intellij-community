/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.*;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ShowAllSubmittedFilesAction;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SvnHistoryProvider implements VcsHistoryProvider {

  private final SvnVcs myVcs;
  private SVNURL myURL;
  private SVNRevision myRevision;
  private boolean myDirectory;

  public SvnHistoryProvider(SvnVcs vcs) {
    this(vcs, null, null, false);
  }

  public SvnHistoryProvider(@NotNull SvnVcs vcs, SVNURL url, SVNRevision revision, boolean isDirectory) {
    myVcs = vcs;
    myURL = url;
    myRevision = revision;
    myDirectory = isDirectory;
  }

  public HistoryAsTreeProvider getTreeHistoryProvider() {
    return null;
  }

  public ColumnInfo[] getRevisionColumns() {
    return new ColumnInfo[] {new CopyFromColumnInfo()};
  }

  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    final FilePath committedPath = ChangesUtil.getCommittedPath(myVcs.getProject(), filePath);
    final List<VcsFileRevision> revisions = getRevisionsList(committedPath);
    if (revisions == null) {
      return null;
    }
    return new VcsHistorySession(revisions) {
      @Nullable
      public VcsRevisionNumber calcCurrentRevisionNumber() {
        return getCurrentRevision(committedPath);
      }

      @Override
      public boolean isContentAvailable(final VcsFileRevision revision) {
        return !myDirectory;
      }
    };
  }

  @Nullable
  private List<VcsFileRevision> getRevisionsList(final FilePath file) throws VcsException {
    final SVNException[] exception = new SVNException[1];
    final ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(SvnBundle.message("progress.text2.collecting.history", file.getName()));
        }
        try {
          if (myURL == null) {
            collectLogEntries(indicator, file, exception, result);
          } else {
            collectLogEntriesForRepository(indicator, result);
          }
        }
        catch(SVNCancelException ex) {
          throw new ProcessCanceledException(ex);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      boolean success = ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("progress.title.revisions.history"), true, myVcs.getProject());
      if (!success) {
        return null;
      }
    }
    else {
      command.run();
    }

    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return result;
  }

  private void collectLogEntries(final ProgressIndicator indicator, FilePath file, SVNException[] exception, final ArrayList<VcsFileRevision> result) throws SVNException {
    SVNWCClient wcClient = myVcs.createWCClient();
    SVNInfo info = wcClient.doInfo(new File(file.getIOFile().getAbsolutePath()), SVNRevision.WORKING);
    if (info == null) {
        exception[0] = new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "File ''{0}'' is not under version control", file.getIOFile()));
        return;
    }
    final String url = info.getURL() == null ? null : info.getURL().toString();
    String relativeUrl = url;
    final SVNURL repoRootURL = info.getRepositoryRootURL();
    if (repoRootURL != null) {
      final String root = repoRootURL.toString();
      if (url != null && url.startsWith(root)) {
        relativeUrl = url.substring(root.length());
      }
    }
    if (indicator != null) {
      indicator.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", url));
    }
    final SVNRevision pegRevision = info.getRevision();
    SVNLogClient client = myVcs.createLogClient();
    client.doLog(new File[]{new File(file.getIOFile().getAbsolutePath())}, SVNRevision.HEAD, SVNRevision.create(1), false, true, 0,
                 new MyLogEntryHandler(url, pegRevision, relativeUrl, result));
  }

  private void collectLogEntriesForRepository(final ProgressIndicator indicator, final ArrayList<VcsFileRevision> result) throws SVNException {
    if (indicator != null) {
      indicator.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", myURL.toString()));
    }
    SVNWCClient wcClient = myVcs.createWCClient();
    SVNInfo info = wcClient.doInfo(myURL, SVNRevision.UNDEFINED, SVNRevision.HEAD);
    final String root = info.getRepositoryRootURL().toString();
    String url = myURL.toString();
    String relativeUrl = url;
    if (url.startsWith(root)) {
      relativeUrl = url.substring(root.length());
    }
    SVNLogClient client = myVcs.createLogClient();
    client.doLog(myURL, new String[] {}, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(1), false, true, 0,
                 new RepositoryLogEntryHandler(url, SVNRevision.UNDEFINED, relativeUrl, result));
  }

  public String getHelpId() {
    return null;
  }

  @Nullable
  public VcsRevisionNumber getCurrentRevision(FilePath file) {
    if (myRevision != null) {
      return new SvnRevisionNumber(myRevision);
    }
    try {
      SVNWCClient wcClient = myVcs.createWCClient();
      SVNInfo info = wcClient.doInfo(new File(file.getPath()).getAbsoluteFile(), SVNRevision.WORKING);
      if (info != null) {
        return new SvnRevisionNumber(info.getCommittedRevision());
      } else {
        return null;
      }
    }
    catch (SVNException e) {
      return null;
    }
  }

  public AnAction[] getAdditionalActions(final FileHistoryPanel panel) {
    return new AnAction[]{new ShowAllSubmittedFilesAction()};
  }

  public boolean isDateOmittable() {
    return false;
  }

  private class MyLogEntryHandler implements ISVNLogEntryHandler {
    private final ProgressIndicator myIndicator;
    private String myLastPath;
    protected final ArrayList<VcsFileRevision> myResult;
    private final SVNRevision myPegRevision;
    private final String myUrl;

    public MyLogEntryHandler(final String url, final SVNRevision pegRevision, String lastPath, final ArrayList<VcsFileRevision> result) {
      myLastPath = lastPath;
      myIndicator = ProgressManager.getInstance().getProgressIndicator();
      myResult = result;
      myPegRevision = pegRevision;
      myUrl = url;
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
      if (myIndicator != null) {
        if (myIndicator.isCanceled()) {
          SVNErrorManager.cancel(SvnBundle.message("exception.text.update.operation.cancelled"));
        }
        myIndicator.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
      }
      String copyPath = null;
      SVNLogEntryPath entryPath = (SVNLogEntryPath)logEntry.getChangedPaths().get(myLastPath);
      if (entryPath != null) {
        copyPath = entryPath.getCopyPath();
      }
      else {
        String path = SVNPathUtil.removeTail(myLastPath);
        while(path.length() > 0) {
          entryPath = (SVNLogEntryPath) logEntry.getChangedPaths().get(path);
          if (entryPath != null) {
            String relativePath = myLastPath.substring(entryPath.getPath().length());
            copyPath = entryPath.getCopyPath() + relativePath;
            break;
          }
          path = SVNPathUtil.removeTail(path);
        }
      }
      if (copyPath != null) {
        myLastPath = copyPath;
      }
      addResultRevision(logEntry, copyPath);
    }

    protected void addResultRevision(final SVNLogEntry logEntry, final String copyPath) {
      Date date = logEntry.getDate();
      String author = logEntry.getAuthor();
      String message = logEntry.getMessage();
      SVNRevision rev = SVNRevision.create(logEntry.getRevision());
      myResult.add(new SvnFileRevision(myVcs, myPegRevision, rev, myUrl, author, date, message, copyPath));
    }
  }

  private class RepositoryLogEntryHandler extends MyLogEntryHandler {
    public RepositoryLogEntryHandler(final String url, final SVNRevision pegRevision, String lastPath, final ArrayList<VcsFileRevision> result) {
      super(url, pegRevision, lastPath, result);
    }

    @Override
    protected void addResultRevision(final SVNLogEntry logEntry, final String copyPath) {
      myResult.add(new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, logEntry, myURL.toString(), copyPath));
    }
  }

  private static class CopyFromColumnInfo extends ColumnInfo<VcsFileRevision, String> {
    private Icon myIcon = IconLoader.getIcon("/actions/menu-copy.png");
    private ColoredTableCellRenderer myRenderer = new ColoredTableCellRenderer() {
      protected void customizeCellRenderer(final JTable table, final Object value, final boolean selected, final boolean hasFocus, final int row, final int column) {
        if (value instanceof String && ((String) value).length() > 0) {
          setIcon(myIcon);
          setToolTipText(SvnBundle.message("copy.column.tooltip", value));
        }
        else {
          setToolTipText("");
        }
      }
    };

    public CopyFromColumnInfo() {
      super(SvnBundle.message("copy.column.title"));
    }

    public String valueOf(final VcsFileRevision o) {
      return o instanceof SvnFileRevision ? ((SvnFileRevision) o).getCopyFromPath() : "";
    }

    @Override
    public TableCellRenderer getRenderer(final VcsFileRevision vcsFileRevision) {
      return myRenderer;
    }

    @Override
    public String getMaxStringValue() {
      return SvnBundle.message("copy.column.title");
    }

    @Override
    public int getAdditionalWidth() {
      return 6;
    }
  }
}
