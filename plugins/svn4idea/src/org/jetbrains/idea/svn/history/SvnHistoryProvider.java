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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class SvnHistoryProvider implements VcsHistoryProvider, VcsCacheableHistorySessionFactory<Boolean, SvnHistoryProvider.MyHistorySession> {
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
    if (((MyHistorySession) session).isSupports15()) {
      final MergeSourceColumnInfo mergeSourceColumn = new MergeSourceColumnInfo((MyHistorySession)session);
      columns = new ColumnInfo[] {new CopyFromColumnInfo(), mergeSourceColumn};

      final JTextArea field = new JTextArea();
      field.setEditable(false);
      field.setBackground(UIUtil.getComboBoxDisabledBackground());
      field.setWrapStyleWord(true);
      listener = new Consumer<VcsFileRevision>() {
        public void consume(VcsFileRevision vcsFileRevision) {
          field.setText(mergeSourceColumn.getText(vcsFileRevision));
        }
      };
      final JPanel panel = new JPanel(new GridBagLayout());
      final GridBagConstraints gb =
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);

      final JLabel mergeLabel = new JLabel("Merge Sources:");
      final MergeSourceDetailsAction sourceAction = new MergeSourceDetailsAction();
      sourceAction.registerSelf(forShortcutRegistration);
      final DefaultActionGroup group = new DefaultActionGroup();
      group.add(sourceAction);
      final JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();

      panel.add(mergeLabel, gb);
      ++ gb.gridx;
      gb.insets.left = 10;
      gb.anchor = GridBagConstraints.NORTHWEST;
      panel.add(toolbar, gb);

      ++ gb.gridy;
      gb.insets.left = 0;
      gb.gridx = 0;
      gb.gridwidth = 2;
      gb.weightx = gb.weighty = 1;
      gb.fill = GridBagConstraints.BOTH;
      panel.add(ScrollPaneFactory.createScrollPane(field), gb);
      addComp = panel;
    } else {
      columns = new ColumnInfo[] {new CopyFromColumnInfo()};
      addComp = null;
      listener = null;
    }
    return new VcsDependentHistoryComponents(columns, listener, addComp);
  }

  @Override
  public FilePath getUsedFilePath(MyHistorySession session) {
    return session.getCommittedPath();
  }

  @Override
  public Boolean getAddinionallyCachedData(MyHistorySession session) {
    return session.isSupports15();
  }

  @Override
  public MyHistorySession createFromCachedData(Boolean aBoolean,
                                               @NotNull List<VcsFileRevision> revisions,
                                               @NotNull FilePath filePath,
                                               VcsRevisionNumber currentRevision) {
    return new MyHistorySession(revisions, filePath, aBoolean, currentRevision);
  }

  class MyHistorySession extends VcsAbstractHistorySession {
    private final FilePath myCommittedPath;
    private final boolean mySupports15;

    private MyHistorySession(final List<VcsFileRevision> revisions, final FilePath committedPath, final boolean supports15,
                             @Nullable final VcsRevisionNumber currentRevision) {
      super(revisions, currentRevision);
      myCommittedPath = committedPath;
      mySupports15 = supports15;
      shouldBeRefreshed();
    }

    public HistoryAsTreeProvider getHistoryAsTreeProvider() {
      return null;
    }

    @Nullable
    public VcsRevisionNumber calcCurrentRevisionNumber() {
      if (myCommittedPath == null) {
        return null;
      }
      return getCurrentRevision(myCommittedPath);
    }

    public FilePath getCommittedPath() {
      return myCommittedPath;
    }

    @Override
    public boolean isContentAvailable(final VcsFileRevision revision) {
      return ! myCommittedPath.isDirectory();
    }

    public boolean isSupports15() {
      return mySupports15;
    }

    @Override
    public VcsHistorySession copy() {
      return new MyHistorySession(getRevisionList(), myCommittedPath, mySupports15, getCurrentRevisionNumber());
    }
  }

  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    final FilePath committedPath = ChangesUtil.getCommittedPath(myVcs.getProject(), filePath);
    final Ref<Boolean> supports15Ref = new Ref<Boolean>();
    final List<VcsFileRevision> revisions = new ArrayList<VcsFileRevision>();
    getRevisionsList(committedPath, supports15Ref, new CollectConsumer<VcsFileRevision>(revisions));
    return new MyHistorySession(revisions, committedPath, Boolean.TRUE.equals(supports15Ref.get()), null);
  }

  public void reportAppendableHistory(FilePath path, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final FilePath committedPath = ChangesUtil.getCommittedPath(myVcs.getProject(), path);
    final Ref<Boolean> supports15Ref = new Ref<Boolean>();

    final MyHistorySession historySession =
      new MyHistorySession(Collections.<VcsFileRevision>emptyList(), committedPath, Boolean.TRUE.equals(supports15Ref.get()), null);

    final Ref<Boolean> sessionReported = new Ref<Boolean>();

    getRevisionsList(committedPath, supports15Ref, new Consumer<VcsFileRevision>() {
      public void consume(VcsFileRevision vcsFileRevision) {
        if (! Boolean.TRUE.equals(sessionReported.get())) {
          partner.reportCreatedEmptySession(historySession);
          sessionReported.set(true);
        }
        partner.acceptRevision(vcsFileRevision);
      }
    });
  }

  @Nullable
  private void getRevisionsList(final FilePath file, final Ref<Boolean> supports15Ref,
                                                 final Consumer<VcsFileRevision> consumer) throws VcsException {
    final VcsException[] exception = new VcsException[1];

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
          indicator.setText(SvnBundle.message("progress.text2.collecting.history", file.getName()));
        }
        try {
          if (! file.isNonLocal()) {
            collectLogEntries(indicator, file, exception, consumer, supports15Ref);
          } else {
            collectLogEntriesForRepository(indicator, file, consumer, supports15Ref);
          }
        }
        catch(SVNCancelException ex) {
          throw new ProcessCanceledException(ex);
        }
        catch (SVNException e) {
          exception[0] = new VcsException(e);
        }
        catch (VcsException e) {
          exception[0] = e;
        }
      }
    };

    command.run();

    if (exception[0] != null) {
      throw exception[0];
    }
  }

  private void collectLogEntries(final ProgressIndicator indicator, FilePath file, VcsException[] exception,
                                 final Consumer<VcsFileRevision> result, final Ref<Boolean> supports15Ref) throws SVNException,
                                                                                                                  VcsException {
    SVNWCClient wcClient = myVcs.createWCClient();
    SVNInfo info = wcClient.doInfo(new File(file.getIOFile().getAbsolutePath()), SVNRevision.WORKING);
    wcClient.setEventHandler(new ISVNEventHandler() {
      public void handleEvent(SVNEvent event, double progress) throws SVNException {
      }

      public void checkCancelled() throws SVNCancelException {
        indicator.checkCanceled();
      }
    });
    if (info == null || info.getRepositoryRootURL() == null) {
        exception[0] = new VcsException("File ''{0}'' is not under version control" + file.getIOFile());
        return;
    }
    final String url = info.getURL() == null ? null : info.getURL().toString();
    String relativeUrl = url;
    final SVNURL repoRootURL = info.getRepositoryRootURL();

    final String root = repoRootURL.toString();
    if (url != null && url.startsWith(root)) {
      relativeUrl = url.substring(root.length());
    }
    if (indicator != null) {
      indicator.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", url));
    }
    final SVNRevision pegRevision = info.getRevision();
    SVNLogClient client = myVcs.createLogClient();

    final boolean supports15 = SvnUtil.checkRepositoryVersion15(myVcs, url);
    supports15Ref.set(supports15);
    client.doLog(new File[]{new File(file.getIOFile().getAbsolutePath())}, SVNRevision.HEAD, SVNRevision.create(1), SVNRevision.UNDEFINED,
                 false, true, supports15, 0, null,
                 new MyLogEntryHandler(url, pegRevision, relativeUrl, result, repoRootURL));
  }

  private void collectLogEntriesForRepository(final ProgressIndicator indicator, FilePath file, final Consumer<VcsFileRevision> result,
                                              final Ref<Boolean> supports15Ref) throws SVNException, VcsException {
    final String url = file.getPath().replace('\\', '/');
    if (indicator != null) {
      indicator.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", url));
    }
    SVNWCClient wcClient = myVcs.createWCClient();
    final SVNURL svnurl = SVNURL.parseURIEncoded(url);
    SVNInfo info = wcClient.doInfo(svnurl, SVNRevision.UNDEFINED, SVNRevision.HEAD);
    final String root = info.getRepositoryRootURL().toString();
    String relativeUrl = url;
    if (url.startsWith(root)) {
      relativeUrl = url.substring(root.length());
    }
    SVNLogClient client = myVcs.createLogClient();
    final boolean supports15 = SvnUtil.checkRepositoryVersion15(myVcs, root);
    supports15Ref.set(supports15);
    // todo log in history provider
    client.doLog(svnurl, new String[] {}, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNRevision.create(1), false, true, supports15, 0, null,
                 new RepositoryLogEntryHandler(url, SVNRevision.UNDEFINED, relativeUrl, result, info.getRepositoryRootURL()));
  }

  public String getHelpId() {
    return null;
  }

  @Nullable
  private VcsRevisionNumber getCurrentRevision(FilePath file) {
    if (file.isNonLocal()) {
      // technically, it does not make sense, since there's no "current" revision for non-local history (if look how it's used)
      // but ok, lets keep it for now
      return new SvnRevisionNumber(SVNRevision.HEAD);
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

  public AnAction[] getAdditionalActions(final Runnable refresher) {
    return new AnAction[]{new ShowAllAffectedGenericAction(), new MergeSourceDetailsAction()};
  }

  public boolean isDateOmittable() {
    return false;
  }

  private class MyLogEntryHandler implements ISVNLogEntryHandler {
    private final ProgressIndicator myIndicator;
    private String myLastPath;
    protected final Consumer<VcsFileRevision> myResult;
    private VcsFileRevision myPrevious;
    private final SVNRevision myPegRevision;
    protected final String myUrl;
    private final SvnMergeSourceTracker myTracker;
    private SVNURL myRepositoryRoot;

    public MyLogEntryHandler(final String url,
                             final SVNRevision pegRevision,
                             String lastPath,
                             final Consumer<VcsFileRevision> result,
                             SVNURL repoRootURL)
      throws SVNException, VcsException {
      myLastPath = lastPath;
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

          final int mergeLevel = svnLogEntryIntegerPair.getSecond();
          final SvnFileRevision revision = createRevision(logEntry, copyPath);
          if (copyPath != null) {
            myLastPath = copyPath;
          }
          if (mergeLevel >= 0) {
            addToListByLevel((SvnFileRevision) myPrevious, revision, mergeLevel);
          } else {
            myResult.consume(revision);
            myPrevious = revision;
          }
        }
      });
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
      if (! sources.isEmpty()) {
        addToListByLevel(sources.get(sources.size() - 1), revisionToAdd, level - 1);
      }
    }

    protected SvnFileRevision createRevision(final SVNLogEntry logEntry, final String copyPath) throws SVNException {
      Date date = logEntry.getDate();
      String author = logEntry.getAuthor();
      String message = logEntry.getMessage();
      SVNRevision rev = SVNRevision.create(logEntry.getRevision());
      final SVNURL url = myRepositoryRoot.appendPath(myLastPath, true);
      return new SvnFileRevision(myVcs, myPegRevision, rev, url.toString(), author, date, message, copyPath);
    }
  }

  private class RepositoryLogEntryHandler extends MyLogEntryHandler {
    public RepositoryLogEntryHandler(final String url,
                                     final SVNRevision pegRevision,
                                     String lastPath,
                                     final Consumer<VcsFileRevision> result,
                                     SVNURL repoRootURL)
      throws VcsException, SVNException {
      super(url, pegRevision, lastPath, result, repoRootURL);
    }

    @Override
    protected SvnFileRevision createRevision(final SVNLogEntry logEntry, final String copyPath) {
      return new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, logEntry, myUrl, copyPath);
    }
  }

  private class MergeSourceColumnInfo extends ColumnInfo<VcsFileRevision, VcsFileRevision> {
    private final MergeSourceRenderer myRenderer;

    private MergeSourceColumnInfo(final MyHistorySession session) {
      super("Merge Sources");
      myRenderer = new MergeSourceRenderer(session);
    }

    @Override
    public TableCellRenderer getRenderer(final VcsFileRevision vcsFileRevision) {
      return myRenderer;
    }

    public VcsFileRevision valueOf(final VcsFileRevision vcsFileRevision) {
      return vcsFileRevision;
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

    public void mouseClicked(final MouseEvent e) {
      if (e.getButton() == 1 && !e.isPopupTrigger()) {
        Object tag = getTagAt(e);
        if (tag == myTag) {
          final SvnFileRevision revision = getSelectedRevision(e);
          if (revision != null) {
            SvnMergeSourceDetails.showMe(myVcs.getProject(), revision, myFile);
          }
        }
      }
    }

    @Nullable
    private SvnFileRevision getSelectedRevision(final MouseEvent e) {
      JTable table = (JTable)e.getSource();
      int row = table.rowAtPoint(e.getPoint());
      int column = table.columnAtPoint(e.getPoint());

      final Object value = table.getModel().getValueAt(row, column);
      if (value instanceof SvnFileRevision) {
        return (SvnFileRevision) value;
      }
      return null;
    }

    public void mouseMoved(MouseEvent e) {
      JTable table = (JTable) e.getSource();
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

    private MergeSourceRenderer(final MyHistorySession session) {
      myFile = session.getCommittedPath().getVirtualFile();
    }

    public String getText(final VcsFileRevision value) {
      if (! (value instanceof SvnFileRevision)) return "";
      final SvnFileRevision revision = (SvnFileRevision) value;
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
        if (! source.getMergeSources().isEmpty()) {
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
        myListener.install(table);
      }
      if (! (value instanceof SvnFileRevision)) {
        append("", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        return;
      }
      final SvnFileRevision revision = (SvnFileRevision) value;
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
    private final Icon myIcon = IconLoader.getIcon("/actions/menu-copy.png");
    private final ColoredTableCellRenderer myRenderer = new ColoredTableCellRenderer() {
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
