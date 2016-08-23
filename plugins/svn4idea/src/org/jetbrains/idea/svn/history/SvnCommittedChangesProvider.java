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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.branchConfig.ConfigureBranchesAction;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusConsumer;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class SvnCommittedChangesProvider implements CachingCommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> {

  private final static Logger LOG = Logger.getInstance(SvnCommittedChangesProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final SvnVcs myVcs;
  @NotNull private final MessageBusConnection myConnection;
  private MergeInfoUpdatesListener myMergeInfoUpdatesListener;
  @NotNull private final SvnCommittedListsZipper myZipper;

  public final static int VERSION_WITH_COPY_PATHS_ADDED = 2;
  public final static int VERSION_WITH_REPLACED_PATHS = 3;

  public SvnCommittedChangesProvider(@NotNull Project project) {
    myProject = project;
    myVcs = SvnVcs.getInstance(myProject);
    myZipper = new SvnCommittedListsZipper(myVcs);

    myConnection = myProject.getMessageBus().connect();

    myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE, new VcsConfigurationChangeListener.DetailedNotification() {
      public void execute(final Project project, final VirtualFile vcsRoot, final List<CommittedChangeList> cachedList) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (project.isDisposed()) {
              return;
            }
            for (CommittedChangeList committedChangeList : cachedList) {
              if ((committedChangeList instanceof SvnChangeList) &&
                  ((vcsRoot == null) || (vcsRoot.equals(((SvnChangeList)committedChangeList).getVcsRoot())))) {
                ((SvnChangeList) committedChangeList).forceReloadCachedInfo(true);
              }
            }
          }
        });
      }
    });
  }

  @NotNull
  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  @NotNull
  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(final boolean showDateFilter) {
    return new SvnVersionFilterComponent(showDateFilter);
  }

  @Nullable
  public RepositoryLocation getLocationFor(@NotNull FilePath root) {
    final String url = SvnUtil.getExactLocation(myVcs, root.getIOFile());

    return url == null ? null : new SvnRepositoryLocation(url, root);
  }

  @Nullable
  public RepositoryLocation getLocationFor(@NotNull FilePath root, @Nullable String repositoryPath) {
    return repositoryPath == null ? getLocationFor(root) : new SvnRepositoryLocation(repositoryPath);
  }

  @NotNull
  public VcsCommittedListsZipper getZipper() {
    return myZipper;
  }

  public void loadCommittedChanges(@NotNull ChangeBrowserSettings settings,
                                   @NotNull RepositoryLocation location,
                                   int maxCount,
                                   @NotNull final AsynchConsumer<CommittedChangeList> consumer) throws VcsException {
    try {
      final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
      final String repositoryRoot = getRepositoryRoot(svnLocation);
      final ChangeBrowserSettings.Filter filter = settings.createFilter();

      getCommittedChangesImpl(settings, svnLocation, maxCount, new Consumer<LogEntry>() {
        public void consume(final LogEntry svnLogEntry) {
          final SvnChangeList cl = new SvnChangeList(myVcs, svnLocation, svnLogEntry, repositoryRoot);
          if (filter.accepts(cl)) {
            consumer.consume(cl);
          }
        }
      }, false, true);
    }
    finally {
      consumer.finished();
    }
  }

  @NotNull
  public List<SvnChangeList> getCommittedChanges(@NotNull ChangeBrowserSettings settings,
                                                 @NotNull RepositoryLocation location,
                                                 int maxCount) throws VcsException {
    final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
    final ArrayList<SvnChangeList> result = new ArrayList<>();
    final String repositoryRoot = getRepositoryRoot(svnLocation);

    getCommittedChangesImpl(settings, svnLocation, maxCount, new Consumer<LogEntry>() {
      public void consume(final LogEntry svnLogEntry) {
        result.add(new SvnChangeList(myVcs, svnLocation, svnLogEntry, repositoryRoot));
      }
    }, false, true);
    settings.filterChanges(result);
    return result;
  }

  public void getCommittedChangesWithMergedRevisons(@NotNull ChangeBrowserSettings settings,
                                                    @NotNull RepositoryLocation location,
                                                    int maxCount,
                                                    @NotNull final PairConsumer<SvnChangeList, LogHierarchyNode> finalConsumer)
    throws VcsException {
    final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
    final String repositoryRoot = getRepositoryRoot(svnLocation);

    final MergeSourceHierarchyBuilder builder = new MergeSourceHierarchyBuilder(new Consumer<LogHierarchyNode>() {
      public void consume(LogHierarchyNode node) {
        finalConsumer.consume(new SvnChangeList(myVcs, svnLocation, node.getMe(), repositoryRoot), node);
      }
    });
    final SvnMergeSourceTracker mergeSourceTracker = new SvnMergeSourceTracker(new ThrowableConsumer<Pair<LogEntry, Integer>, SVNException>() {
      public void consume(Pair<LogEntry, Integer> svnLogEntryIntegerPair) throws SVNException {
        builder.consume(svnLogEntryIntegerPair);
      }
    });

    getCommittedChangesImpl(settings, SvnTarget.fromURL(svnLocation.toSvnUrl()), maxCount, new Consumer<LogEntry>() {
      public void consume(final LogEntry svnLogEntry) {
        try {
          mergeSourceTracker.consume(svnLogEntry);
        }
        catch (SVNException e) {
          throw new RuntimeException(e);
          // will not occur actually but anyway never eat them
        }
      }
    }, true, false);

    builder.finish();
  }

  @NotNull
  private String getRepositoryRoot(@NotNull SvnRepositoryLocation svnLocation) throws VcsException {
    // TODO: Additionally SvnRepositoryLocation could possibly be refactored to always contain FilePath (or similar local item)
    // TODO: So here we could get repository url without performing remote svn command

    SVNURL rootUrl = SvnUtil.getRepositoryRoot(myVcs, svnLocation.toSvnUrl());

    if (rootUrl == null) {
      throw new SvnBindException("Could not resolve repository root url for " + svnLocation);
    }

    return rootUrl.toDecodedString();
  }

  private void getCommittedChangesImpl(@NotNull ChangeBrowserSettings settings,
                                       @NotNull SvnRepositoryLocation location,
                                       int maxCount,
                                       @NotNull Consumer<LogEntry> resultConsumer,
                                       boolean includeMergedRevisions,
                                       boolean filterOutByDate) throws VcsException {
    SvnTarget target = SvnTarget.fromURL(location.toSvnUrl(), createBeforeRevision(settings));

    getCommittedChangesImpl(settings, target, maxCount, resultConsumer, includeMergedRevisions, filterOutByDate);
  }

  private void getCommittedChangesImpl(@NotNull ChangeBrowserSettings settings,
                                       @NotNull SvnTarget target,
                                       int maxCount,
                                       @NotNull Consumer<LogEntry> resultConsumer,
                                       boolean includeMergedRevisions,
                                       boolean filterOutByDate) throws VcsException {
    setCollectingChangesProgress(target.getPathOrUrlString());

    String author = settings.getUserFilter();
    SVNRevision revisionBefore = createBeforeRevision(settings);
    SVNRevision revisionAfter = createAfterRevision(settings);

    myVcs.getFactory(target).createHistoryClient()
      .doLog(target, revisionBefore, revisionAfter, settings.STOP_ON_COPY, true, includeMergedRevisions, maxCount, null,
             createLogHandler(resultConsumer, filterOutByDate, author));
  }

  @NotNull
  private static SVNRevision createBeforeRevision(@NotNull ChangeBrowserSettings settings) {
    return createRevision(settings.getDateBeforeFilter(), settings.getChangeBeforeFilter(), SVNRevision.HEAD);
  }

  @NotNull
  private static SVNRevision createAfterRevision(@NotNull ChangeBrowserSettings settings) {
    return createRevision(settings.getDateAfterFilter(), settings.getChangeAfterFilter(), SVNRevision.create(1));
  }

  @NotNull
  private static SVNRevision createRevision(@Nullable Date date, @Nullable Long change, @NotNull SVNRevision defaultValue) {
    final SVNRevision result;

    if (date != null) {
      result = SVNRevision.create(date);
    }
    else if (change != null) {
      result = SVNRevision.create(change.longValue());
    }
    else {
      result = defaultValue;
    }

    return result;
  }

  @NotNull
  private LogEntryConsumer createLogHandler(@NotNull final Consumer<LogEntry> resultConsumer,
                                            final boolean filterOutByDate,
                                            @Nullable final String author) {
    return new LogEntryConsumer() {
      @Override
      public void consume(LogEntry logEntry) {
        if (myProject.isDisposed()) throw new ProcessCanceledException();

        ProgressManager.progress2(SvnBundle.message("progress.text2.processing.revision", logEntry.getRevision()));
        if (filterOutByDate && logEntry.getDate() == null) {
          // do not add lists without info - this situation is possible for lists where there are paths that user has no rights to observe
          return;
        }
        if (author == null || author.equalsIgnoreCase(logEntry.getAuthor())) {
          resultConsumer.consume(logEntry);
        }
      }
    };
  }

  private static void setCollectingChangesProgress(@Nullable Object location) {
    ProgressManager.progress(SvnBundle.message("progress.text.changes.collecting.changes"),
                             SvnBundle.message("progress.text2.changes.establishing.connection", location));
  }

  @NotNull
  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] {
      new ChangeListColumn.ChangeListNumberColumn(SvnBundle.message("revision.title")),
      ChangeListColumn.NAME, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION
    };
  }

  private void refreshMergeInfo(@NotNull RootsAndBranches action) {
    if (myMergeInfoUpdatesListener == null) {
      myMergeInfoUpdatesListener = new MergeInfoUpdatesListener(myProject, myConnection);
    }
    myMergeInfoUpdatesListener.addPanel(action);
  }

  @NotNull
  public VcsCommittedViewAuxiliary createActions(@NotNull DecoratorManager manager, @Nullable RepositoryLocation location) {
    final RootsAndBranches rootsAndBranches = new RootsAndBranches(myVcs, manager, location);
    refreshMergeInfo(rootsAndBranches);

    final DefaultActionGroup popup = new DefaultActionGroup(myVcs.getDisplayName(), true);
    popup.add(rootsAndBranches.getIntegrateAction());
    popup.add(rootsAndBranches.getUndoIntegrateAction());
    popup.add(new ConfigureBranchesAction());

    final ShowHideMergePanelAction action = new ShowHideMergePanelAction(manager, rootsAndBranches.getStrategy());

    return new VcsCommittedViewAuxiliary(Collections.<AnAction>singletonList(popup), new Runnable() {
      public void run() {
        if (myMergeInfoUpdatesListener != null) {
          myMergeInfoUpdatesListener.removePanel(rootsAndBranches);
          rootsAndBranches.dispose();
        }
      }
    }, Collections.<AnAction>singletonList(action));
  }

  public int getUnlimitedCountValue() {
    return 0;
  }

  @Nullable
  @Override
  public Pair<SvnChangeList, FilePath> getOneList(@NotNull VirtualFile file, @NotNull VcsRevisionNumber number) throws VcsException {
    return new SingleCommittedListProvider(myVcs, file, number).run();
  }

  @NotNull
  @Override
  public RepositoryLocation getForNonLocal(@NotNull VirtualFile file) {
    return new SvnRepositoryLocation(FileUtil.toSystemIndependentName(file.getPresentableUrl()));
  }

  @Override
  public boolean supportsIncomingChanges() {
    return true;
  }

  public int getFormatVersion() {
    return VERSION_WITH_REPLACED_PATHS;
  }

  public void writeChangeList(@NotNull DataOutput dataStream, @NotNull SvnChangeList list) throws IOException {
    list.writeToStream(dataStream);
  }

  @NotNull
  public SvnChangeList readChangeList(@NotNull RepositoryLocation location, @NotNull DataInput stream) throws IOException {
    final int version = getFormatVersion();
    return new SvnChangeList(myVcs, (SvnRepositoryLocation)location, stream, VERSION_WITH_COPY_PATHS_ADDED <= version,
                             VERSION_WITH_REPLACED_PATHS <= version);
  }

  public boolean isMaxCountSupported() {
    return true;
  }

  @Nullable
  public Collection<FilePath> getIncomingFiles(@NotNull RepositoryLocation location) throws VcsException {
    FilePath root = null;

    if (Registry.is("svn.use.incoming.optimization")) {
      root = ((SvnRepositoryLocation)location).getRoot();

      if (root == null) {
        LOG.info("Working copy root is not provided for repository location " + location);
      }
    }

    return root != null ? getIncomingFiles(root) : null;
  }

  @NotNull
  private Collection<FilePath> getIncomingFiles(@NotNull FilePath root) throws SvnBindException {
    // TODO: "svn diff -r BASE:HEAD --xml --summarize" command is also suitable here and outputs only necessary changed files,
    // TODO: while "svn status -u" also outputs other files which could be not modified on server. But for svn 1.7 "--xml --summarize"
    // TODO: could only be used with url targets - so we could not use "svn diff" here now for all cases (we could not use url with
    // TODO: concrete revision as there could be mixed revision working copy).

    final Set<FilePath> result = ContainerUtil.newHashSet();
    File rootFile = root.getIOFile();

    myVcs.getFactory(rootFile).createStatusClient()
      .doStatus(rootFile, SVNRevision.UNDEFINED, Depth.INFINITY, true, false, false, false, new StatusConsumer() {
        @Override
        public void consume(Status status) throws SVNException {
          File file = status.getFile();
          boolean changedOnServer = isNotNone(status.getRemoteContentsStatus()) ||
                                    isNotNone(status.getRemoteNodeStatus()) ||
                                    isNotNone(status.getRemotePropertiesStatus());

          if (file != null && changedOnServer) {
            result.add(VcsUtil.getFilePath(file, file.isDirectory()));
          }
        }
      }, null);

    return result;
  }

  private static boolean isNotNone(@Nullable StatusType status) {
    return status != null && !StatusType.STATUS_NONE.equals(status);
  }

  public boolean refreshCacheByNumber() {
    return true;
  }

  public String getChangelistTitle() {
    return SvnBundle.message("changes.browser.revision.term");
  }

  public boolean isChangeLocallyAvailable(FilePath filePath, @Nullable VcsRevisionNumber localRevision, VcsRevisionNumber changeRevision,
                                          final SvnChangeList changeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  public boolean refreshIncomingWithCommitted() {
    return true;
  }

  public void deactivate() {
    myConnection.disconnect();
  }
}
