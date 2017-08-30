/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;

public class SvnHistoryProvider
  implements VcsHistoryProvider, VcsCacheableHistorySessionFactory<Boolean, SvnHistorySession> {
  private final SvnVcs myVcs;

  public SvnHistoryProvider(SvnVcs vcs) {
    myVcs = vcs;
  }

  @Override
  public boolean supportsHistoryForDirectories() {
    return true;
  }

  @Override
  public DiffFromHistoryHandler getHistoryDiffHandler() {
    return new SvnDiffFromHistoryHandler(myVcs);
  }

  @Override
  public boolean canShowHistoryFor(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    final ColumnInfo[] columns;
    final Consumer<VcsFileRevision> listener;
    final JComponent addComp;
    if (((SvnHistorySession)session).isHaveMergeSources()) {
      final MergeSourceColumnInfo mergeSourceColumn = new MergeSourceColumnInfo((SvnHistorySession)session);
      columns = new ColumnInfo[]{new CopyFromColumnInfo(), mergeSourceColumn};

      final JTextArea field = new JTextArea() {
        final StatusText statusText = new StatusText(this) {
          @Override
          protected boolean isStatusVisible() {
            return getDocument().getLength() == 0;
          }
        };

        @Override
        public Color getBackground() {
          return UIUtil.getEditorPaneBackground();
        }

        {
          statusText.setText("Merge sources");
          setWrapStyleWord(true);
          setLineWrap(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          statusText.paint(this, g);
        }
      };
      field.setEditable(false);
      field.setOpaque(false);
      field.setWrapStyleWord(true);
      listener = vcsFileRevision -> {
        field.setText(mergeSourceColumn.getText(vcsFileRevision));
        field.setCaretPosition(0);
        field.repaint();
      };

      final MergeSourceDetailsAction sourceAction = new MergeSourceDetailsAction();
      sourceAction.registerSelf(forShortcutRegistration);

      JPanel fieldPanel = new ToolbarDecorator() {
        @Override
        protected JComponent getComponent() {
          return field;
        }

        @Override
        protected void updateButtons() {
        }

        @Override
        protected void installDnDSupport() {
        }

        @Override
        protected boolean isModelEditable() {
          return false;
        }
      }.initPosition()
        .addExtraAction(AnActionButton.fromAction(sourceAction))
        .createPanel();
      fieldPanel.setBorder(JBUI.Borders.empty());
      addComp = fieldPanel;
    }
    else {
      columns = new ColumnInfo[]{new CopyFromColumnInfo()};
      addComp = null;
      listener = null;
    }
    return new VcsDependentHistoryComponents(columns, listener, addComp);
  }

  @Override
  public FilePath getUsedFilePath(SvnHistorySession session) {
    return session.getCommittedPath();
  }

  @Override
  public Boolean getAddinionallyCachedData(SvnHistorySession session) {
    return session.isHaveMergeSources();
  }

  @Override
  public SvnHistorySession createFromCachedData(Boolean aBoolean,
                                               @NotNull List<VcsFileRevision> revisions,
                                               @NotNull FilePath filePath,
                                               VcsRevisionNumber currentRevision) {
    return new SvnHistorySession(myVcs, revisions, filePath, aBoolean, currentRevision, false, ! filePath.isNonLocal());
  }

  @Override
  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    final VcsAppendableHistoryPartnerAdapter adapter = new VcsAppendableHistoryPartnerAdapter();
    reportAppendableHistory(filePath, adapter);
    adapter.check();

    return adapter.getSession();
  }

  @Override
  public void reportAppendableHistory(FilePath path, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    // request MAXIMUM_HISTORY_ROWS + 1 log entries to be able to detect if there are more log entries than it is configured to show -
    // see LimitHistoryCheck
    VcsConfiguration configuration = VcsConfiguration.getInstance(myVcs.getProject());
    int limit = configuration.LIMIT_HISTORY ? configuration.MAXIMUM_HISTORY_ROWS + 1 : 0;

    reportAppendableHistory(path, partner, null, null, limit, null, false);
  }

  public void reportAppendableHistory(FilePath path, final VcsAppendableHistorySessionPartner partner,
                                      @Nullable final SVNRevision from, @Nullable final SVNRevision to, final int limit,
                                      SVNRevision peg, final boolean forceBackwards) throws VcsException {
    FilePath committedPath = path;
    Change change = ChangeListManager.getInstance(myVcs.getProject()).getChange(path);
    if (change != null) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision != null && afterRevision != null && !beforeRevision.getFile().equals(afterRevision.getFile()) &&
          afterRevision.getFile().equals(path)) {
        committedPath = beforeRevision.getFile();
      }
      // revision can be VcsRevisionNumber.NULL
      if (peg == null && change.getBeforeRevision() != null && change.getBeforeRevision().getRevisionNumber() instanceof SvnRevisionNumber) {
        peg = ((SvnRevisionNumber) change.getBeforeRevision().getRevisionNumber()).getRevision();
      }
    }

    boolean showMergeSources = myVcs.getSvnConfiguration().isShowMergeSourcesInAnnotate();
    final LogLoader logLoader;
    if (path.isNonLocal()) {
      logLoader = new RepositoryLoader(myVcs, committedPath, from, to, limit, peg, forceBackwards, showMergeSources);
    }
    else {
      logLoader = new LocalLoader(myVcs, committedPath, from, to, limit, peg, showMergeSources);
    }

    logLoader.preliminary();
    logLoader.check();
    if (showMergeSources) {
      logLoader.initSupports15();
    }

    final SvnHistorySession historySession =
      new SvnHistorySession(myVcs, Collections.emptyList(), committedPath, showMergeSources && Boolean.TRUE.equals(logLoader.mySupport15), null, false,
                            ! path.isNonLocal());

    final Ref<Boolean> sessionReported = new Ref<>();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(SvnBundle.message("progress.text2.collecting.history", path.getName()));
    }
    final Consumer<VcsFileRevision> consumer = vcsFileRevision -> {
      if (!Boolean.TRUE.equals(sessionReported.get())) {
        partner.reportCreatedEmptySession(historySession);
        sessionReported.set(true);
      }
      partner.acceptRevision(vcsFileRevision);
    };

    logLoader.setConsumer(consumer);
    logLoader.load();
    logLoader.check();
  }

  private static abstract class LogLoader {
    protected final boolean myShowMergeSources;
    protected String myUrl;
    protected boolean mySupport15;
    protected final SvnVcs myVcs;
    protected final FilePath myFile;
    protected final SVNRevision myFrom;
    protected final SVNRevision myTo;
    protected final int myLimit;
    protected final SVNRevision myPeg;
    protected Consumer<VcsFileRevision> myConsumer;
    protected final ProgressIndicator myPI;
    protected VcsException myException;

    protected LogLoader(SvnVcs vcs, FilePath file, SVNRevision from, SVNRevision to, int limit, SVNRevision peg, boolean showMergeSources) {
      myVcs = vcs;
      myFile = file;
      myFrom = from;
      myTo = to;
      myLimit = limit;
      myPeg = peg;
      myPI = ProgressManager.getInstance().getProgressIndicator();
      myShowMergeSources = showMergeSources;
    }

    public void setConsumer(Consumer<VcsFileRevision> consumer) {
      myConsumer = consumer;
    }

    protected void initSupports15() {
      assert myUrl != null;
      mySupport15 = SvnUtil.checkRepositoryVersion15(myVcs, myUrl);
    }

    public void check() throws VcsException {
      if (myException != null) throw myException;
    }

    protected abstract void preliminary();

    protected abstract void load();
  }

  private static class LocalLoader extends LogLoader {
    private Info myInfo;

    private LocalLoader(SvnVcs vcs, FilePath file, SVNRevision from, SVNRevision to, int limit, SVNRevision peg, boolean showMergeSources) {
      super(vcs, file, from, to, limit, peg, showMergeSources);
    }

    @Override
    protected void preliminary() {
      myInfo = myVcs.getInfo(myFile.getIOFile());
      if (myInfo == null || myInfo.getRepositoryRootURL() == null) {
        myException = new VcsException("File " + myFile.getPath() + " is not under version control");
        return;
      }
      if (myInfo.getURL() == null) {
        myException = new VcsException("File " + myFile.getPath() + " is not under Subversion control");
        return;
      }
      myUrl = myInfo.getURL().toDecodedString();
    }

    @Override
    protected void load() {
      SVNURL repoRootURL = myInfo.getRepositoryRootURL();
      String relativeUrl = getRelativeUrl(repoRootURL.toDecodedString(), myUrl);
      
      if (myPI != null) {
        myPI.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", myUrl));
      }
      final SVNRevision pegRevision = myInfo.getRevision();
      final SvnTarget target = SvnTarget.fromFile(myFile.getIOFile(), myPeg);
      try {
        myVcs.getFactory(target).createHistoryClient().doLog(
          target,
          myFrom == null ? SVNRevision.HEAD : myFrom,
          myTo == null ? SVNRevision.create(1) : myTo,
          false, true, myShowMergeSources && mySupport15, myLimit, null,
          new MyLogEntryHandler(myVcs, myUrl, pegRevision, relativeUrl,
                                createConsumerAdapter(myConsumer),
                                repoRootURL, myFile.getCharset()));
      }
      catch (VcsException e) {
        myException = e;
      }
    }
  }

  private static ThrowableConsumer<VcsFileRevision, SVNException> createConsumerAdapter(final Consumer<VcsFileRevision> consumer) {
    return revision -> consumer.consume(revision);
  }

  private static class RepositoryLoader extends LogLoader {
    private final boolean myForceBackwards;

    private RepositoryLoader(SvnVcs vcs,
                             FilePath file,
                             SVNRevision from,
                             SVNRevision to,
                             int limit,
                             SVNRevision peg,
                             boolean forceBackwards, boolean showMergeSources) {
      super(vcs, file, from, to, limit, peg, showMergeSources);
      myForceBackwards = forceBackwards;
    }

    @Override
    protected void preliminary() {
      myUrl = myFile.getPath().replace('\\', '/');
    }

    @Override
    protected void load() {
      if (myPI != null) {
        myPI.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", myUrl));
      }

      try {
        if (myForceBackwards) {
          SVNURL svnurl = SVNURL.parseURIEncoded(myUrl);
          if (! existsNow(svnurl)) {
            loadBackwards(svnurl);
            return;
          }
        }

        final SVNURL svnurl = SVNURL.parseURIEncoded(myUrl);
        SVNRevision operationalFrom = myFrom == null ? SVNRevision.HEAD : myFrom;
        // TODO: try to rewrite without separately retrieving repository url by item url - as this command could require authentication
        // TODO: and it is not "clear enough/easy to implement" with current design (for some cases) how to cache credentials (if in
        // TODO: non-interactive mode)
        final SVNURL rootURL = SvnUtil.getRepositoryRoot(myVcs, svnurl);
        if (rootURL == null) {
          throw new VcsException("Could not find repository root for URL: " + myUrl);
        }
        String relativeUrl = getRelativeUrl(rootURL.toDecodedString(), myUrl);
        SvnTarget target = SvnTarget.fromURL(svnurl, myPeg == null ? myFrom : myPeg);
        RepositoryLogEntryHandler handler =
          new RepositoryLogEntryHandler(myVcs, myUrl, SVNRevision.UNDEFINED, relativeUrl, createConsumerAdapter(myConsumer), rootURL);

        myVcs.getFactory(target).createHistoryClient()
          .doLog(target, operationalFrom, myTo == null ? SVNRevision.create(1) : myTo, false, true, myShowMergeSources && mySupport15,
                 myLimit, null, handler);
      }
      catch (SVNCancelException e) {
        //
      }
      catch (SVNException e) {
        myException = new VcsException(e);
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    private void loadBackwards(SVNURL svnurl) throws VcsException {
      // this method is called when svnurl does not exist in latest repository revision - thus concrete old revision is used for "info"
      // command to get repository url
      Info info = myVcs.getInfo(svnurl, myPeg, myPeg);
      if (info == null || info.getRepositoryRootURL() == null) {
        throw new VcsException("Could not find repository root for URL: " + svnurl + " in revision " + myPeg);
      }

      SVNURL rootURL = info.getRepositoryRootURL();
      String relativeUrl = getRelativeUrl(rootURL.toDecodedString(), myUrl);
      final RepositoryLogEntryHandler repositoryLogEntryHandler =
        new RepositoryLogEntryHandler(myVcs, myUrl, SVNRevision.UNDEFINED, relativeUrl, revision -> myConsumer.consume(revision), rootURL);
      repositoryLogEntryHandler.setThrowCancelOnMeetPathCreation(true);

      SvnTarget target = SvnTarget.fromURL(rootURL, myFrom);
      myVcs.getFactory(target).createHistoryClient()
        .doLog(target, myFrom, myTo == null ? SVNRevision.create(1) : myTo, false, true, myShowMergeSources && mySupport15, 1, null,
               repositoryLogEntryHandler);
    }

    private boolean existsNow(SVNURL svnurl) {
      final Info info;
      try {
        info = myVcs.getInfo(svnurl, SVNRevision.HEAD, SVNRevision.HEAD);
      }
      catch (SvnBindException e) {
        return false;
      }
      return info != null && info.getURL() != null && info.getRevision().isValid();
    }
  }

  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  public AnAction[] getAdditionalActions(final Runnable refresher) {
    return new AnAction[]{ShowAllAffectedGenericAction.getInstance(),
      ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER), new MergeSourceDetailsAction(),
      new SvnEditCommitMessageFromFileHistoryAction()};
  }

  @Override
  public boolean isDateOmittable() {
    return false;
  }

  private static class MyLogEntryHandler implements LogEntryConsumer {
    private final ProgressIndicator myIndicator;
    protected final SvnVcs myVcs;
    protected final SvnPathThroughHistoryCorrection myLastPathCorrector;
    private final Charset myCharset;
    protected final ThrowableConsumer<VcsFileRevision, SVNException> myResult;
    private final String myLastPath;
    private VcsFileRevision myPrevious;
    private final SVNRevision myPegRevision;
    protected final String myUrl;
    private final SvnMergeSourceTracker myTracker;
    protected SVNURL myRepositoryRoot;
    private boolean myThrowCancelOnMeetPathCreation;

    public void setThrowCancelOnMeetPathCreation(boolean throwCancelOnMeetPathCreation) {
      myThrowCancelOnMeetPathCreation = throwCancelOnMeetPathCreation;
    }

    public MyLogEntryHandler(SvnVcs vcs, final String url,
                             final SVNRevision pegRevision,
                             String lastPath,
                             final ThrowableConsumer<VcsFileRevision, SVNException> result,
                             SVNURL repoRootURL, Charset charset) {
      myVcs = vcs;
      myLastPathCorrector = new SvnPathThroughHistoryCorrection(lastPath);
      myLastPath = lastPath;
      myCharset = charset;
      myIndicator = ProgressManager.getInstance().getProgressIndicator();
      myResult = result;
      myPegRevision = pegRevision;
      myUrl = url;
      myRepositoryRoot = repoRootURL;
      myTracker = new SvnMergeSourceTracker(svnLogEntryIntegerPair -> {
        final LogEntry logEntry = svnLogEntryIntegerPair.getFirst();

        if (myIndicator != null) {
          if (myIndicator.isCanceled()) {
            SVNErrorManager.cancel(SvnBundle.message("exception.text.update.operation.cancelled"), SVNLogType.DEFAULT);
          }
          myIndicator.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
        }
        LogEntryPath entryPath = null;
        String copyPath = null;
        final int mergeLevel = svnLogEntryIntegerPair.getSecond();

        if (!myLastPathCorrector.isRoot()) {
          myLastPathCorrector.consume(logEntry);
          entryPath = myLastPathCorrector.getDirectlyMentioned();
          copyPath = null;
          if (entryPath != null) {
            copyPath = entryPath.getCopyPath();
          }
          else {
            // if there are no path with exact match, check whether parent or child paths had changed
            // "entry path" is allowed to be null now; if it is null, last path would be taken for revision construction

            // Separate LogEntry is issued for each "merge source" revision. These "merge source" revisions are treated as child
            // revisions of some other revision - this way we construct merge hierarchy.
            // mergeLevel >= 0 indicates that we are currently processing some "merge source" revision. This "merge source" revision
            // contains changes from some other branch - so checkForChildChanges() and checkForParentChanges() return "false".
            // Because of this case we apply these methods only for non-"merge source" revisions - this means mergeLevel < 0.
            // TODO: Do not apply path filtering even for log entries on the first level => just output of 'svn log' should be returned.
            // TODO: Looks like there is no cases when we issue 'svn log' for some parent paths or some other cases where we need such
            // TODO: filtering. Check user feedback on this.
//              if (mergeLevel < 0 && !checkForChildChanges(logEntry) && !checkForParentChanges(logEntry)) return;
          }
        }

        final SvnFileRevision revision = createRevision(logEntry, copyPath, entryPath);
        if (mergeLevel >= 0) {
          addToListByLevel((SvnFileRevision)myPrevious, revision, mergeLevel);
        }
        else {
          myResult.consume(revision);
          myPrevious = revision;
        }
        if (myThrowCancelOnMeetPathCreation && myUrl.equals(revision.getURL()) && entryPath != null && entryPath.getType() == 'A') {
          throw new SVNCancelException();
        }
      });
    }

    private boolean checkForParentChanges(LogEntry logEntry) {
      final String lastPathBefore = myLastPathCorrector.getBefore();
      String path = SVNPathUtil.removeTail(lastPathBefore);
      while (path.length() > 0) {
        final LogEntryPath entryPath = logEntry.getChangedPaths().get(path);
        // A & D are checked since we are not interested in parent folders property changes, only in structure changes
        // TODO: seems that R (replaced) should also be checked here
        if (entryPath != null && (entryPath.getType() == 'A' || entryPath.getType() == 'D')) {
          if (entryPath.getCopyPath() != null) {
            return true;
          }
          break;
        }
        path = SVNPathUtil.removeTail(path);
      }
      return false;
    }

    // TODO: this makes sense only for directories, but should always return true if something under the directory was changed in revision
    // TODO: as svn will provide child changes in history for directory
    private boolean checkForChildChanges(LogEntry logEntry) {
      final String lastPathBefore = myLastPathCorrector.getBefore();
      for (String key : logEntry.getChangedPaths().keySet()) {
        if (SVNPathUtil.isAncestor(lastPathBefore, key)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void consume(LogEntry logEntry) throws SVNException {
      myTracker.consume(logEntry);
    }

    private static void addToListByLevel(final SvnFileRevision revision, final SvnFileRevision revisionToAdd, final int level) {
      if (level < 0) {
        return;
      }
      if (level == 0) {
        revision.addMergeSource(revisionToAdd);
        return;
      }
      final List<SvnFileRevision> sources = revision.getMergeSources();
      if (!sources.isEmpty()) {
        addToListByLevel(sources.get(sources.size() - 1), revisionToAdd, level - 1);
      }
    }

    protected SvnFileRevision createRevision(final LogEntry logEntry, final String copyPath, LogEntryPath entryPath) throws SVNException {
      Date date = logEntry.getDate();
      String author = logEntry.getAuthor();
      String message = logEntry.getMessage();
      SVNRevision rev = SVNRevision.create(logEntry.getRevision());
      final SVNURL url = myRepositoryRoot.appendPath(myLastPath, true);
//      final SVNURL url = entryPath != null ? myRepositoryRoot.appendPath(entryPath.getPath(), true) :
//                         myRepositoryRoot.appendPath(myLastPathCorrector.getBefore(), false);
      return new SvnFileRevision(myVcs, myPegRevision, rev, url.toString(), author, date, message, copyPath);
    }
  }

  private static class RepositoryLogEntryHandler extends MyLogEntryHandler {
    public RepositoryLogEntryHandler(final SvnVcs vcs, final String url,
                                     final SVNRevision pegRevision,
                                     String lastPath,
                                     final ThrowableConsumer<VcsFileRevision, SVNException> result,
                                     SVNURL repoRootURL) {
      super(vcs, url, pegRevision, lastPath, result, repoRootURL, null);
    }

    @Override
    protected SvnFileRevision createRevision(final LogEntry logEntry, final String copyPath, LogEntryPath entryPath)
      throws SVNException {
      final SVNURL url = entryPath == null ? myRepositoryRoot.appendPath(myLastPathCorrector.getBefore(), false) :
                         myRepositoryRoot.appendPath(entryPath.getPath(), true);
      return new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, logEntry, url.toString(), copyPath);
    }
  }

  private static class RevisionMergeSourceInfo {

    @NotNull private final VcsFileRevision revision;

    private RevisionMergeSourceInfo(@NotNull VcsFileRevision revision) {
      this.revision = revision;
    }

    @NotNull
    public SvnFileRevision getRevision() {
      return (SvnFileRevision)revision;
    }

    // will be used, for instance, while copying (to clipboard) data from table
    @Override
    public String toString() {
      return toString(revision);
    }

    private static String toString(@Nullable VcsFileRevision value) {
      if (!(value instanceof SvnFileRevision)) return "";
      final SvnFileRevision revision = (SvnFileRevision)value;
      final List<SvnFileRevision> mergeSources = revision.getMergeSources();
      if (mergeSources.isEmpty()) {
        return "";
      }
      final StringBuilder sb = new StringBuilder();
      for (SvnFileRevision source : mergeSources) {
        if (sb.length() != 0) {
          sb.append(", ");
        }
        sb.append(source.getRevisionNumber().asString());
        if (!source.getMergeSources().isEmpty()) {
          sb.append("*");
        }
      }
      return sb.toString();
    }
  }

  private class MergeSourceColumnInfo extends ColumnInfo<VcsFileRevision, RevisionMergeSourceInfo> {
    private final MergeSourceRenderer myRenderer;

    private MergeSourceColumnInfo(final SvnHistorySession session) {
      super("Merge Sources");
      myRenderer = new MergeSourceRenderer(session);
    }

    @Override
    public TableCellRenderer getRenderer(final VcsFileRevision vcsFileRevision) {
      return myRenderer;
    }

    @Override
    public RevisionMergeSourceInfo valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision != null ? new RevisionMergeSourceInfo(vcsFileRevision) : null;
    }

    public String getText(final VcsFileRevision vcsFileRevision) {
      return myRenderer.getText(vcsFileRevision);
    }

    @Override
    public int getAdditionalWidth() {
      return 20;
    }

    @Override
    public String getPreferredStringValue() {
      return "1234567, 1234567, 1234567";
    }
  }

  private static final Object MERGE_SOURCE_DETAILS_TAG = new Object();

  private class MergeSourceDetailsLinkListener extends TableLinkMouseListener {
    private final VirtualFile myFile;
    private final Object myTag;

    private MergeSourceDetailsLinkListener(final Object tag, final VirtualFile file) {
      myTag = tag;
      myFile = file;
    }

    @Override
    public boolean onClick(@NotNull MouseEvent e, int clickCount) {
      if (e.getButton() == 1 && !e.isPopupTrigger()) {
        Object tag = getTagAt(e);
        if (tag == myTag) {
          final SvnFileRevision revision = getSelectedRevision(e);
          if (revision != null) {
            SvnMergeSourceDetails.showMe(myVcs.getProject(), revision, myFile);
            return true;
          }
        }
      }
      return false;
    }

    @Nullable
    private SvnFileRevision getSelectedRevision(final MouseEvent e) {
      JTable table = (JTable)e.getSource();
      int row = table.rowAtPoint(e.getPoint());
      int column = table.columnAtPoint(e.getPoint());

      final Object value = table.getModel().getValueAt(row, column);
      if (value instanceof RevisionMergeSourceInfo) {
        return ((RevisionMergeSourceInfo)value).getRevision();
      }
      return null;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      JTable table = (JTable)e.getSource();
      Object tag = getTagAt(e);
      if (tag == myTag) {
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      else {
        table.setCursor(Cursor.getDefaultCursor());
      }
    }
  }

  private class MergeSourceRenderer extends ColoredTableCellRenderer {
    private MergeSourceDetailsLinkListener myListener;
    private final VirtualFile myFile;

    private MergeSourceRenderer(final SvnHistorySession session) {
      myFile = session.getCommittedPath().getVirtualFile();
    }

    public String getText(final VcsFileRevision value) {
      return RevisionMergeSourceInfo.toString(value);
    }

    @Override
    protected void customizeCellRenderer(final JTable table,
                                         final Object value,
                                         final boolean selected,
                                         final boolean hasFocus,
                                         final int row,
                                         final int column) {
      if (myListener == null) {
        myListener = new MergeSourceDetailsLinkListener(MERGE_SOURCE_DETAILS_TAG, myFile);
        myListener.installOn(table);
      }
      appendMergeSourceText(table, row, column, value instanceof RevisionMergeSourceInfo ? value.toString() : null);
    }

    private void appendMergeSourceText(JTable table, int row, int column, @Nullable String text) {
      if (StringUtil.isEmpty(text)) {
        append("", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        append(cutString(text, table.getCellRect(row, column, false).getWidth()), SimpleTextAttributes.REGULAR_ATTRIBUTES,
               MERGE_SOURCE_DETAILS_TAG);
      }
    }

    private String cutString(final String text, final double value) {
      final FontMetrics m = getFontMetrics(getFont());
      final Graphics g = getGraphics();

      if (m.getStringBounds(text, g).getWidth() < value) return text;

      final String dots = "...";
      final double dotsWidth = m.getStringBounds(dots, g).getWidth();
      if (dotsWidth >= value) {
        return dots;
      }

      for (int i = 1; i < text.length(); i++) {
        if ((m.getStringBounds(text, 0, i, g).getWidth() + dotsWidth) >= value) {
          if (i < 2) return dots;
          return text.substring(0, i - 1) + dots;
        }
      }
      return text;
    }
  }

  private static class CopyFromColumnInfo extends ColumnInfo<VcsFileRevision, String> {
    private final Icon myIcon = PlatformIcons.COPY_ICON;
    private final ColoredTableCellRenderer myRenderer = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(final JTable table,
                                           final Object value,
                                           final boolean selected,
                                           final boolean hasFocus,
                                           final int row,
                                           final int column) {
        if (value instanceof String && ((String)value).length() > 0) {
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

    @Override
    public String valueOf(final VcsFileRevision o) {
      return o instanceof SvnFileRevision ? ((SvnFileRevision)o).getCopyFromPath() : "";
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
