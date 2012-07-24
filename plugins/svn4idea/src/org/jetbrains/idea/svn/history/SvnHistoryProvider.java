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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNLogType;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SvnHistoryProvider
  implements VcsHistoryProvider, VcsCacheableHistorySessionFactory<Boolean, SvnHistorySession> {
  private final SvnVcs myVcs;

  public SvnHistoryProvider(SvnVcs vcs) {
    myVcs = vcs;
  }

  public boolean supportsHistoryForDirectories() {
    return true;
  }

  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    final ColumnInfo[] columns;
    final Consumer<VcsFileRevision> listener;
    final JComponent addComp;
    if (((SvnHistorySession)session).isSupports15()) {
      final MergeSourceColumnInfo mergeSourceColumn = new MergeSourceColumnInfo((SvnHistorySession)session);
      columns = new ColumnInfo[]{new CopyFromColumnInfo(), mergeSourceColumn};

      final JPanel panel = new JPanel(new BorderLayout());

      final JTextArea field = new JTextArea();
      field.setEditable(false);
      field.setBackground(UIUtil.getComboBoxDisabledBackground());
      field.setWrapStyleWord(true);
      listener = new Consumer<VcsFileRevision>() {
        public void consume(VcsFileRevision vcsFileRevision) {
          field.setText(mergeSourceColumn.getText(vcsFileRevision));
        }
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
      fieldPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP));

      panel.add(fieldPanel, BorderLayout.CENTER);
      panel.add(new JLabel("Merge Sources:"), BorderLayout.NORTH);
      addComp = panel;
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
    return session.isSupports15();
  }

  @Override
  public SvnHistorySession createFromCachedData(Boolean aBoolean,
                                               @NotNull List<VcsFileRevision> revisions,
                                               @NotNull FilePath filePath,
                                               VcsRevisionNumber currentRevision) {
    return new SvnHistorySession(myVcs, revisions, filePath, aBoolean, currentRevision, false, ! filePath.isNonLocal());
  }

  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    final VcsAppendableHistoryPartnerAdapter adapter = new VcsAppendableHistoryPartnerAdapter();
    reportAppendableHistory(filePath, adapter);
    adapter.check();

    return adapter.getSession();
  }

  public void reportAppendableHistory(FilePath path, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    reportAppendableHistory(path, partner, null, null, 0, null, false);
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

    final LogLoader logLoader;
    if (path.isNonLocal()) {
      logLoader = new RepositoryLoader(myVcs, path, from, to, limit, peg, forceBackwards);
    }
    else {
      logLoader = new LocalLoader(myVcs, path, from, to, limit, peg);
    }

    try {
      logLoader.preliminary();
    }
    catch (SVNCancelException e) {
      throw new VcsException(e);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    logLoader.check();
    logLoader.initSupports15();

    final SvnHistorySession historySession =
      new SvnHistorySession(myVcs, Collections.<VcsFileRevision>emptyList(), committedPath, Boolean.TRUE.equals(logLoader.mySupport15), null, false,
                            ! path.isNonLocal());

    final Ref<Boolean> sessionReported = new Ref<Boolean>();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(SvnBundle.message("progress.text2.collecting.history", path.getName()));
    }
    final Consumer<VcsFileRevision> consumer = new Consumer<VcsFileRevision>() {
      public void consume(VcsFileRevision vcsFileRevision) {
        if (!Boolean.TRUE.equals(sessionReported.get())) {
          partner.reportCreatedEmptySession(historySession);
          sessionReported.set(true);
        }
        partner.acceptRevision(vcsFileRevision);
      }
    };

    logLoader.setConsumer(consumer);
    logLoader.load();
    logLoader.check();
  }

  private static abstract class LogLoader {
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

    protected LogLoader(SvnVcs vcs, FilePath file, SVNRevision from, SVNRevision to, int limit, SVNRevision peg) {
      myVcs = vcs;
      myFile = file;
      myFrom = from;
      myTo = to;
      myLimit = limit;
      myPeg = peg;
      myPI = ProgressManager.getInstance().getProgressIndicator();
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

    protected abstract void preliminary() throws SVNException;

    protected abstract void load();
  }

  private static class LocalLoader extends LogLoader {
    private SVNInfo myInfo;

    private LocalLoader(SvnVcs vcs, FilePath file, SVNRevision from, SVNRevision to, int limit, SVNRevision peg) {
      super(vcs, file, from, to, limit, peg);
    }

    @Override
    protected void preliminary() throws SVNException {
      SVNWCClient wcClient = myVcs.createWCClient();
      myInfo = wcClient.doInfo(new File(myFile.getIOFile().getAbsolutePath()), SVNRevision.UNDEFINED);
      wcClient.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(SVNEvent event, double progress) throws SVNException {
        }

        public void checkCancelled() throws SVNCancelException {
          myPI.checkCanceled();
        }
      });
      if (myInfo == null || myInfo.getRepositoryRootURL() == null) {
        myException = new VcsException("File ''{0}'' is not under version control" + myFile.getIOFile());
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
      String relativeUrl = myUrl;
      final SVNURL repoRootURL = myInfo.getRepositoryRootURL();

      final String root = repoRootURL.toString();
      if (myUrl != null && myUrl.startsWith(root)) {
        relativeUrl = myUrl.substring(root.length());
      }
      if (myPI != null) {
        myPI.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", myUrl));
      }
      final SVNRevision pegRevision = myInfo.getRevision();
      SVNLogClient client = myVcs.createLogClient();
      try {
        client
          .doLog(new File[]{new File(myFile.getIOFile().getAbsolutePath())},
                 myFrom == null ? SVNRevision.HEAD : myFrom, myTo == null ? SVNRevision.create(1) : myTo, myPeg,
                 false, true, mySupport15, myLimit, null,
                 new MyLogEntryHandler(myVcs, myUrl, pegRevision, relativeUrl, createConsumerAdapter(myConsumer), repoRootURL, myFile.getCharset()));
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
  }

  private static ThrowableConsumer<VcsFileRevision, SVNException> createConsumerAdapter(final Consumer<VcsFileRevision> consumer) {
    return new ThrowableConsumer<VcsFileRevision, SVNException>() {
      @Override
      public void consume(VcsFileRevision revision) throws SVNException {
        consumer.consume(revision);
      }
    };
  }

  private static class RepositoryLoader extends LogLoader {
    private final boolean myForceBackwards;

    private RepositoryLoader(SvnVcs vcs,
                             FilePath file,
                             SVNRevision from,
                             SVNRevision to,
                             int limit,
                             SVNRevision peg,
                             boolean forceBackwards) {
      super(vcs, file, from, to, limit, peg);
      myForceBackwards = forceBackwards;
    }

    @Override
    protected void preliminary() throws SVNException {
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

        SVNWCClient wcClient = myVcs.createWCClient();
        final SVNURL svnurl = SVNURL.parseURIEncoded(myUrl);
        SVNRevision operationalFrom = myFrom == null ? SVNRevision.HEAD : myFrom;
        final SVNURL rootURL = getRepositoryRoot(svnurl, myFrom);
        final String root = rootURL.toString();
        String relativeUrl = myUrl;
        if (myUrl.startsWith(root)) {
          relativeUrl = myUrl.substring(root.length());
        }
        SVNLogClient client = myVcs.createLogClient();
        client.doLog(svnurl, new String[]{}, myPeg == null ? myFrom : myPeg,
                     operationalFrom, myTo == null ? SVNRevision.create(1) : myTo, false, true, mySupport15, myLimit, null,
                     new RepositoryLogEntryHandler(myVcs, myUrl, SVNRevision.UNDEFINED, relativeUrl, createConsumerAdapter(myConsumer), rootURL));
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

    private void loadBackwards(SVNURL svnurl) throws SVNException, VcsException {
        final SVNURL rootURL = getRepositoryRoot(svnurl, myFrom);
        final String root = rootURL.toString();
        String relativeUrl = myUrl;
        if (myUrl.startsWith(root)) {
          relativeUrl = myUrl.substring(root.length());
        }

        SVNLogClient client = myVcs.createLogClient();

        final RepositoryLogEntryHandler repositoryLogEntryHandler =
          new RepositoryLogEntryHandler(myVcs, myUrl, SVNRevision.UNDEFINED, relativeUrl,
                                        new ThrowableConsumer<VcsFileRevision, SVNException>() {
                                          @Override
                                          public void consume(VcsFileRevision revision) throws SVNException {
                                            myConsumer.consume(revision);
                                            throw new SVNCancelException(); // load only one revision
                                          }
                                        }, rootURL);
        repositoryLogEntryHandler.setThrowCancelOnMeetPathCreation(true);

        client.doLog(rootURL, new String[]{}, myFrom, myFrom, myTo == null ? SVNRevision.create(1) : myTo, false, true, mySupport15, 0, null, repositoryLogEntryHandler);
    }

    private SVNURL getRepositoryRoot(SVNURL svnurl, SVNRevision operationalFrom) throws SVNException {
      return myVcs.createRepository(svnurl).getRepositoryRoot(false);
      /*final SVNWCClient wcClient = myVcs.createWCClient();
      try {
        final SVNInfo info;
        info = wcClient.doInfo(svnurl, myPeg, operationalFrom);
        return info.getRepositoryRootURL();
      }
      catch (SVNException e) {
        try {
          final SVNInfo info;
          info = wcClient.doInfo(svnurl, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED);
          return info.getRepositoryRootURL();
        } catch (SVNException e1) {
          final SVNInfo info;
          info = wcClient.doInfo(svnurl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
          return info.getRepositoryRootURL();
        }
      }*/
    }

    private boolean existsNow(SVNURL svnurl) {
      final SVNWCClient wcClient = myVcs.createWCClient();
      final SVNInfo info;
      try {
        info = wcClient.doInfo(svnurl, SVNRevision.HEAD, SVNRevision.HEAD);
      }
      catch (SVNException e) {
        return false;
      }
      return info != null && info.getURL() != null && info.getRevision().isValid();
    }
  }

  public String getHelpId() {
    return null;
  }

  public AnAction[] getAdditionalActions(final Runnable refresher) {
    return new AnAction[]{new ShowAllAffectedGenericAction(), new MergeSourceDetailsAction()};
  }

  public boolean isDateOmittable() {
    return false;
  }

  private static class MyLogEntryHandler implements ISVNLogEntryHandler {
    private final ProgressIndicator myIndicator;
    protected final SvnVcs myVcs;
    protected String myLastPath;
    private final Charset myCharset;
    protected final ThrowableConsumer<VcsFileRevision, SVNException> myResult;
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
                             SVNURL repoRootURL, Charset charset)
      throws SVNException, VcsException {
      myVcs = vcs;
      myLastPath = lastPath;
      myCharset = charset;
      myIndicator = ProgressManager.getInstance().getProgressIndicator();
      myResult = result;
      myPegRevision = pegRevision;
      myUrl = url;
      myRepositoryRoot = repoRootURL;
      myTracker = new SvnMergeSourceTracker(new ThrowableConsumer<Pair<SVNLogEntry, Integer>, SVNException>() {
        public void consume(final Pair<SVNLogEntry, Integer> svnLogEntryIntegerPair) throws SVNException {
          final SVNLogEntry logEntry = svnLogEntryIntegerPair.getFirst();

          if (myIndicator != null) {
            if (myIndicator.isCanceled()) {
              SVNErrorManager.cancel(SvnBundle.message("exception.text.update.operation.cancelled"), SVNLogType.DEFAULT);
            }
            myIndicator.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
          }
          String copyPath = null;
          SVNLogEntryPath entryPath = (SVNLogEntryPath)logEntry.getChangedPaths().get(myLastPath);
          if (entryPath != null) {
            copyPath = entryPath.getCopyPath();
          } else {
            // if there are no path with exact match, check whether parent or child paths had changed
            // "entry path" is allowed to be null now; if it is null, last pa in th would be taken for revision construction
            // if parent path was renamed, last path would be corrected below in correctLastPathAccordingToFolderRenames
            if (! checkForChildChanges(logEntry) && ! checkForParentChanges(logEntry)) return;
          }

          final int mergeLevel = svnLogEntryIntegerPair.getSecond();
          final SvnFileRevision revision = createRevision(logEntry, copyPath, entryPath);
          if (copyPath != null) {
            myLastPath = copyPath;
          } else if (entryPath == null) {
            myLastPath = correctLastPathAccordingToFolderRenames(myLastPath, logEntry);
          }
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
        }

      });
    }

    private boolean checkForParentChanges(SVNLogEntry logEntry) {
      String relativePath = null;
      String path = SVNPathUtil.removeTail(myLastPath);
      while (path.length() > 0) {
        final SVNLogEntryPath entryPath = (SVNLogEntryPath)logEntry.getChangedPaths().get(path);
        if (entryPath != null && (entryPath.getType() == 'A' || entryPath.getType() == 'D')) {
          relativePath = SVNPathUtil.getRelativePath(entryPath.getPath(), myLastPath);
          if (entryPath.getCopyPath() != null) {
            return true;
          }
          break;
        }
        path = SVNPathUtil.removeTail(path);
      }
      return false;
    }

    private boolean checkForChildChanges(SVNLogEntry logEntry) {
      for (String key : logEntry.getChangedPaths().keySet()) {
        if (SVNPathUtil.isAncestor(myLastPath, key)) {
          return true;
        }
      }
      return false;
    }

    private String correctLastPathAccordingToFolderRenames(String lastPath, SVNLogEntry logEntry) {
      final Map<String,SVNLogEntryPath> paths = logEntry.getChangedPaths();
      for (Map.Entry<String, SVNLogEntryPath> entry : paths.entrySet()) {
        final SVNLogEntryPath value = entry.getValue();
        final String copyPath = value.getCopyPath();
        if (copyPath != null) {
          final String entryPath = value.getPath();
          if (SVNPathUtil.isAncestor(entryPath, lastPath)) {
            final String relativePath = SVNPathUtil.getRelativePath(entryPath, lastPath);
            return SVNPathUtil.append(copyPath, relativePath);
          }
        }
      }
      return lastPath;
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
      myTracker.consume(logEntry);
    }

    private void addToListByLevel(final SvnFileRevision revision, final SvnFileRevision revisionToAdd, final int level) {
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

    protected SvnFileRevision createRevision(final SVNLogEntry logEntry, final String copyPath, SVNLogEntryPath entryPath) throws SVNException {
      Date date = logEntry.getDate();
      String author = logEntry.getAuthor();
      String message = logEntry.getMessage();
      SVNRevision rev = SVNRevision.create(logEntry.getRevision());
//      final SVNURL url = myRepositoryRoot.appendPath(myLastPath, true);
      final SVNURL url = entryPath != null ? myRepositoryRoot.appendPath(entryPath.getPath(), true) :
                         myRepositoryRoot.appendPath(myLastPath, true);
      return new SvnFileRevision(myVcs, myPegRevision, rev, url.toString(), author, date, message, copyPath, myCharset);
    }
  }

  private static class RepositoryLogEntryHandler extends MyLogEntryHandler {
    public RepositoryLogEntryHandler(final SvnVcs vcs, final String url,
                                     final SVNRevision pegRevision,
                                     String lastPath,
                                     final ThrowableConsumer<VcsFileRevision, SVNException> result,
                                     SVNURL repoRootURL)
      throws VcsException, SVNException {
      super(vcs, url, pegRevision, lastPath, result, repoRootURL, null);
    }

    @Override
    protected SvnFileRevision createRevision(final SVNLogEntry logEntry, final String copyPath, SVNLogEntryPath entryPath)
      throws SVNException {
      final SVNURL url = entryPath == null ? myRepositoryRoot.appendPath(myLastPath, true) :
                         myRepositoryRoot.appendPath(entryPath.getPath(), true);
      return new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, logEntry, url.toString(), copyPath, null);
    }
  }

  private class MergeSourceColumnInfo extends ColumnInfo<VcsFileRevision, String> {
    private final MergeSourceRenderer myRenderer;

    private MergeSourceColumnInfo(final SvnHistorySession session) {
      super("Merge Sources");
      myRenderer = new MergeSourceRenderer(session);
    }

    @Override
    public TableCellRenderer getRenderer(final VcsFileRevision vcsFileRevision) {
      return myRenderer;
    }

    public String valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision == null ? "" : getText(vcsFileRevision);
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
    public boolean onClick(MouseEvent e, int clickCount) {
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
      if (value instanceof SvnFileRevision) {
        return (SvnFileRevision)value;
      }
      return null;
    }

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
      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        return;
      }
      if (!(value instanceof SvnFileRevision)) {
        append("", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        return;
      }
      final SvnFileRevision revision = (SvnFileRevision)value;
      final String text = getText(revision);
      if (text.length() == 0) {
        append("", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        return;
      }

      append(cutString(text, table.getCellRect(row, column, false).getWidth()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
