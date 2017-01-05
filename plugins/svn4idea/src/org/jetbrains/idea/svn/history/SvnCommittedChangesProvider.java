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

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.openapi.progress.ProgressManager.progress2;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.newHashSet;
import static java.util.Collections.singletonList;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnCommittedChangesProvider implements CachingCommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> {

  private final static Logger LOG = Logger.getInstance(SvnCommittedChangesProvider.class);

  @NotNull private final SvnVcs myVcs;
  @NotNull private final MessageBusConnection myConnection;
  private MergeInfoUpdatesListener myMergeInfoUpdatesListener;
  @NotNull private final SvnCommittedListsZipper myZipper;

  public final static int VERSION_WITH_COPY_PATHS_ADDED = 2;
  public final static int VERSION_WITH_REPLACED_PATHS = 3;

  public SvnCommittedChangesProvider(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myZipper = new SvnCommittedListsZipper(myVcs);

    myConnection = myVcs.getProject().getMessageBus().connect();
    myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED_RESPONSE,
                           (project, vcsRoot, cachedList) -> getApplication().invokeLater(() -> {
                             cachedList.stream().filter(SvnChangeList.class::isInstance).map(SvnChangeList.class::cast)
                               .filter(list -> vcsRoot == null || vcsRoot.equals(list.getVcsRoot()))
                               .forEach(SvnChangeList::forceReloadCachedInfo);
                           }, project.getDisposed()));
  }

  @Override
  @NotNull
  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  @Override
  @NotNull
  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new SvnVersionFilterComponent(showDateFilter);
  }

  @Override
  @Nullable
  public RepositoryLocation getLocationFor(@NotNull FilePath root) {
    String url = SvnUtil.getExactLocation(myVcs, root.getIOFile());

    return url == null ? null : new SvnRepositoryLocation(url, root);
  }

  @Override
  @Nullable
  public RepositoryLocation getLocationFor(@NotNull FilePath root, @Nullable String repositoryPath) {
    return repositoryPath == null ? getLocationFor(root) : new SvnRepositoryLocation(repositoryPath);
  }

  @Override
  @NotNull
  public VcsCommittedListsZipper getZipper() {
    return myZipper;
  }

  @Override
  public void loadCommittedChanges(@NotNull ChangeBrowserSettings settings,
                                   @NotNull RepositoryLocation location,
                                   int maxCount,
                                   @NotNull AsynchConsumer<CommittedChangeList> consumer) throws VcsException {
    try {
      SvnRepositoryLocation svnLocation = (SvnRepositoryLocation)location;
      String repositoryRoot = getRepositoryRoot(svnLocation);
      ChangeBrowserSettings.Filter filter = settings.createFilter();
      Consumer<LogEntry> resultConsumer = logEntry -> {
        SvnChangeList list = new SvnChangeList(myVcs, svnLocation, logEntry, repositoryRoot);
        if (filter.accepts(list)) {
          consumer.consume(list);
        }
      };
      SvnTarget target = SvnTarget.fromURL(svnLocation.toSvnUrl(), createBeforeRevision(settings));

      getCommittedChangesImpl(settings, target, maxCount, resultConsumer, false, true);
    }
    finally {
      consumer.finished();
    }
  }

  @Override
  @NotNull
  public List<SvnChangeList> getCommittedChanges(@NotNull ChangeBrowserSettings settings,
                                                 @NotNull RepositoryLocation location,
                                                 int maxCount) throws VcsException {
    SvnRepositoryLocation svnLocation = (SvnRepositoryLocation)location;
    List<SvnChangeList> result = newArrayList();
    String repositoryRoot = getRepositoryRoot(svnLocation);
    Consumer<LogEntry> resultConsumer = logEntry -> result.add(new SvnChangeList(myVcs, svnLocation, logEntry, repositoryRoot));
    SvnTarget target = SvnTarget.fromURL(svnLocation.toSvnUrl(), createBeforeRevision(settings));

    getCommittedChangesImpl(settings, target, maxCount, resultConsumer, false, true);
    settings.filterChanges(result);
    return result;
  }

  public void getCommittedChangesWithMergedRevisons(@NotNull ChangeBrowserSettings settings,
                                                    @NotNull RepositoryLocation location,
                                                    int maxCount,
                                                    @NotNull PairConsumer<SvnChangeList, LogHierarchyNode> finalConsumer)
    throws VcsException {
    SvnRepositoryLocation svnLocation = (SvnRepositoryLocation)location;
    String repositoryRoot = getRepositoryRoot(svnLocation);
    MergeSourceHierarchyBuilder builder = new MergeSourceHierarchyBuilder(
      node -> finalConsumer.consume(new SvnChangeList(myVcs, svnLocation, node.getMe(), repositoryRoot), node));
    SvnMergeSourceTracker mergeSourceTracker = new SvnMergeSourceTracker(builder);

    getCommittedChangesImpl(settings, SvnTarget.fromURL(svnLocation.toSvnUrl()), maxCount, logEntry -> {
      try {
        mergeSourceTracker.consume(logEntry);
      }
      catch (SVNException e) {
        throw new RuntimeException(e);
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
                                       @NotNull SvnTarget target,
                                       int maxCount,
                                       @NotNull Consumer<LogEntry> resultConsumer,
                                       boolean includeMergedRevisions,
                                       boolean filterOutByDate) throws VcsException {
    progress(message("progress.text.changes.collecting.changes"),
             message("progress.text2.changes.establishing.connection", target.getPathOrUrlString()));

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
    SVNRevision result;

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
  private LogEntryConsumer createLogHandler(@NotNull Consumer<LogEntry> resultConsumer, boolean filterOutByDate, @Nullable String author) {
    return logEntry -> {
      if (myVcs.getProject().isDisposed()) throw new ProcessCanceledException();

      if (logEntry != LogEntry.EMPTY) {
        progress2(message("progress.text2.processing.revision", logEntry.getRevision()));
      }
      if (filterOutByDate && logEntry.getDate() == null) {
        // do not add lists without info - this situation is possible for lists where there are paths that user has no rights to observe
        return;
      }
      if (author == null || author.equalsIgnoreCase(logEntry.getAuthor())) {
        resultConsumer.consume(logEntry);
      }
    };
  }

  @Override
  @NotNull
  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[]{
      new ChangeListColumn.ChangeListNumberColumn(message("revision.title")),
      ChangeListColumn.NAME, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION
    };
  }

  private void refreshMergeInfo(@NotNull RootsAndBranches action) {
    if (myMergeInfoUpdatesListener == null) {
      myMergeInfoUpdatesListener = new MergeInfoUpdatesListener(myVcs.getProject(), myConnection);
    }
    myMergeInfoUpdatesListener.addPanel(action);
  }

  @Override
  @NotNull
  public VcsCommittedViewAuxiliary createActions(@NotNull DecoratorManager manager, @Nullable RepositoryLocation location) {
    RootsAndBranches rootsAndBranches = new RootsAndBranches(myVcs, manager, location);
    refreshMergeInfo(rootsAndBranches);

    DefaultActionGroup popup = new DefaultActionGroup(myVcs.getDisplayName(), true);
    popup.add(rootsAndBranches.getIntegrateAction());
    popup.add(rootsAndBranches.getUndoIntegrateAction());
    popup.add(new ConfigureBranchesAction());

    ShowHideMergePanelAction action = new ShowHideMergePanelAction(manager, rootsAndBranches.getStrategy());

    return new VcsCommittedViewAuxiliary(singletonList(popup), () -> {
      if (myMergeInfoUpdatesListener != null) {
        myMergeInfoUpdatesListener.removePanel(rootsAndBranches);
        rootsAndBranches.dispose();
      }
    }, singletonList(action));
  }

  @Override
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

  @Override
  public int getFormatVersion() {
    return VERSION_WITH_REPLACED_PATHS;
  }

  @Override
  public void writeChangeList(@NotNull DataOutput dataStream, @NotNull SvnChangeList list) throws IOException {
    list.writeToStream(dataStream);
  }

  @Override
  @NotNull
  public SvnChangeList readChangeList(@NotNull RepositoryLocation location, @NotNull DataInput stream) throws IOException {
    int version = getFormatVersion();
    return new SvnChangeList(myVcs, (SvnRepositoryLocation)location, stream, VERSION_WITH_COPY_PATHS_ADDED <= version,
                             VERSION_WITH_REPLACED_PATHS <= version);
  }

  @Override
  public boolean isMaxCountSupported() {
    return true;
  }

  @Override
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

    Set<FilePath> result = newHashSet();
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
            result.add(VcsUtil.getFilePath(file));
          }
        }
      }, null);

    return result;
  }

  private static boolean isNotNone(@Nullable StatusType status) {
    return status != null && !StatusType.STATUS_NONE.equals(status);
  }

  @Override
  public boolean refreshCacheByNumber() {
    return true;
  }

  @Override
  public String getChangelistTitle() {
    return message("changes.browser.revision.term");
  }

  @Override
  public boolean isChangeLocallyAvailable(FilePath filePath,
                                          @Nullable VcsRevisionNumber localRevision,
                                          VcsRevisionNumber changeRevision,
                                          SvnChangeList changeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  @Override
  public boolean refreshIncomingWithCommitted() {
    return true;
  }

  public void deactivate() {
    myConnection.disconnect();
  }
}
