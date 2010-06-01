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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ConfigureBranchesAction;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class SvnCommittedChangesProvider implements CachingCommittedChangesProvider<SvnChangeList, ChangeBrowserSettings> {
  private final Project myProject;
  private final SvnVcs myVcs;
  private final MessageBusConnection myConnection;
  private MergeInfoUpdatesListener myMergeInfoUpdatesListener;
  private final MyZipper myZipper;

  public final static int VERSION_WITH_COPY_PATHS_ADDED = 2;
  public final static int VERSION_WITH_REPLACED_PATHS = 3;

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.SvnCommittedChangesProvider");

  public SvnCommittedChangesProvider(final Project project) {
    myProject = project;
    myVcs = SvnVcs.getInstance(myProject);
    myZipper = new MyZipper();

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

  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(final boolean showDateFilter) {
    return new SvnVersionFilterComponent(showDateFilter);
  }

  @Nullable
  public RepositoryLocation getLocationFor(final FilePath root) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    String[] urls = SvnUtil.getLocationsForModule(myVcs, root.getIOFile(), progress);
    if (urls.length == 1) {
      return new SvnRepositoryLocation(urls [0]);
    }
    return null;
  }

  public RepositoryLocation getLocationFor(final FilePath root, final String repositoryPath) {
    if (repositoryPath == null) {
      return getLocationFor(root);
    }

    return new SvnLoadingRepositoryLocation(repositoryPath, myVcs);
  }

  public VcsCommittedListsZipper getZipper() {
    return myZipper;
  }

  private class MyZipper implements VcsCommittedListsZipper {
    public Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(final List<RepositoryLocation> in) {
      final List<RepositoryLocationGroup> groups = new ArrayList<RepositoryLocationGroup>();
      final List<RepositoryLocation> singles = new ArrayList<RepositoryLocation>();

      final MultiMap<SVNURL, RepositoryLocation> map = new MultiMap<SVNURL, RepositoryLocation>();

      for (RepositoryLocation location : in) {
        final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
        final String url = svnLocation.getURL();

        final SVNURL root = SvnUtil.getRepositoryRoot(myVcs, url);
        if (root == null) {
          // should not occur
          LOG.info("repository root not found for location:"+ location.toPresentableString());
          singles.add(location);
        } else {
          map.putValue(root, svnLocation);
        }
      }

      final Set<SVNURL> keys = map.keySet();
      for (SVNURL key : keys) {
        final Collection<RepositoryLocation> repositoryLocations = map.get(key);
        if (repositoryLocations.size() == 1) {
          singles.add(repositoryLocations.iterator().next());
        } else {
          final SvnRepositoryLocationGroup group = new SvnRepositoryLocationGroup(key, repositoryLocations);
          groups.add(group);
        }
      }
      return new Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>>(groups, singles);
    }

    public CommittedChangeList zip(final RepositoryLocationGroup group, final List<CommittedChangeList> lists) {
      return new SvnChangeList(lists, new SvnRepositoryLocation(group.toPresentableString()));
    }

    public long getNumber(final CommittedChangeList list) {
      return list.getNumber();
    }
  }

  public void loadCommittedChanges(ChangeBrowserSettings settings,
                                   RepositoryLocation location,
                                   int maxCount,
                                   final AsynchConsumer<CommittedChangeList> consumer)
    throws VcsException {
    try {
      final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
      final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.changes.collecting.changes"));
        progress.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", location));
      }

      final String repositoryRoot;
      try {
        final SVNRepository repository = myVcs.createRepository(svnLocation.getURL());
        repositoryRoot = repository.getRepositoryRoot(true).toString();
        repository.closeSession();
      }
      catch (SVNException e) {
        throw new VcsException(e);
      }

      final ChangeBrowserSettings.Filter filter = settings.createFilter();

      getCommittedChangesImpl(settings, svnLocation.getURL(), new String[]{""}, maxCount, new Consumer<SVNLogEntry>() {
        public void consume(final SVNLogEntry svnLogEntry) {
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

  public List<SvnChangeList> getCommittedChanges(ChangeBrowserSettings settings, final RepositoryLocation location, final int maxCount) throws VcsException {
    final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
    final ArrayList<SvnChangeList> result = new ArrayList<SvnChangeList>();
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.setText(SvnBundle.message("progress.text.changes.collecting.changes"));
      progress.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", location));
    }

    final String repositoryRoot;
    try {
      final SVNRepository repository = myVcs.createRepository(svnLocation.getURL());
      repositoryRoot = repository.getRepositoryRoot(true).toString();
      repository.closeSession();
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }

    getCommittedChangesImpl(settings, svnLocation.getURL(), new String[]{""}, maxCount, new Consumer<SVNLogEntry>() {
      public void consume(final SVNLogEntry svnLogEntry) {
        result.add(new SvnChangeList(myVcs, svnLocation, svnLogEntry, repositoryRoot));
      }
    }, false, true);
    settings.filterChanges(result);
    return result;
  }

  public void getCommittedChangesWithMergedRevisons(final ChangeBrowserSettings settings,
                                                                   final RepositoryLocation location, final int maxCount,
                                                                   final PairConsumer<SvnChangeList, TreeStructureNode<SVNLogEntry>> finalConsumer)
    throws VcsException {
    final SvnRepositoryLocation svnLocation = (SvnRepositoryLocation) location;
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.setText(SvnBundle.message("progress.text.changes.collecting.changes"));
      progress.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", location));
    }

    final String repositoryRoot;
    try {
      final SVNRepository repository = myVcs.createRepository(svnLocation.getURL());
      repositoryRoot = repository.getRepositoryRoot(true).toString();
      repository.closeSession();
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }

    final MergeTrackerProxy proxy = new MergeTrackerProxy(new Consumer<TreeStructureNode<SVNLogEntry>>() {
      public void consume(TreeStructureNode<SVNLogEntry> node) {
        finalConsumer.consume(new SvnChangeList(myVcs, svnLocation, node.getMe(), repositoryRoot), node);
      }
    });
    final SvnMergeSourceTracker mergeSourceTracker = new SvnMergeSourceTracker(new ThrowableConsumer<Pair<SVNLogEntry, Integer>, SVNException>() {
      public void consume(Pair<SVNLogEntry, Integer> svnLogEntryIntegerPair) throws SVNException {
        proxy.consume(svnLogEntryIntegerPair);
      }
    });

    getCommittedChangesImpl(settings, svnLocation.getURL(), new String[]{""}, maxCount, new Consumer<SVNLogEntry>() {
      public void consume(final SVNLogEntry svnLogEntry) {
        try {
          mergeSourceTracker.consume(svnLogEntry);
        }
        catch (SVNException e) {
          throw new RuntimeException(e);
          // will not occur actually but anyway never eat them
        }
      }
    }, true, false);
    
    proxy.finish();
  }


  private static class MergeTrackerProxy implements ThrowableConsumer<Pair<SVNLogEntry, Integer>, SVNException> {
    private TreeStructureNode<SVNLogEntry> myCurrentHierarchy;
    private final Consumer<TreeStructureNode<SVNLogEntry>> myConsumer;

    private MergeTrackerProxy(Consumer<TreeStructureNode<SVNLogEntry>> consumer) {
      myConsumer = consumer;
    }

    public void consume(Pair<SVNLogEntry, Integer> svnLogEntryIntegerPair) throws SVNException {
      final SVNLogEntry logEntry = svnLogEntryIntegerPair.getFirst();
      final Integer mergeLevel = svnLogEntryIntegerPair.getSecond();

      if (mergeLevel < 0) {
        if (myCurrentHierarchy != null) {
          myConsumer.consume(myCurrentHierarchy);
        }
        if (logEntry.hasChildren()) {
          myCurrentHierarchy = new TreeStructureNode<SVNLogEntry>(logEntry);
        } else {
          // just pass
          myCurrentHierarchy = null;
          myConsumer.consume(new TreeStructureNode<SVNLogEntry>(logEntry));
        }
      } else {
        addToLevel(myCurrentHierarchy, logEntry, mergeLevel);
      }
    }

    public void finish() {
      if (myCurrentHierarchy != null) {
        myConsumer.consume(myCurrentHierarchy);
      }
    }

    private static void addToLevel(final TreeStructureNode<SVNLogEntry> tree, final SVNLogEntry entry, final int left) {
      assert tree != null;
      if (left == 0) {
        tree.add(entry);
      } else {
        final List<TreeStructureNode<SVNLogEntry>> children = tree.getChildren();
        assert ! children.isEmpty();
        addToLevel(children.get(children.size() - 1), entry, left - 1);
      }
    }
  }

  private void getCommittedChangesImpl(ChangeBrowserSettings settings, final String url, final String[] filterUrls,
                                       final int maxCount, final Consumer<SVNLogEntry> resultConsumer, final boolean includeMergedRevisions,
                                       final boolean filterOutByDate) throws VcsException {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.setText(SvnBundle.message("progress.text.changes.collecting.changes"));
      progress.setText2(SvnBundle.message("progress.text2.changes.establishing.connection", url));
    }
    try {
      SVNLogClient logger = myVcs.createLogClient();

      final String author = settings.getUserFilter();
      final Date dateFrom = settings.getDateAfterFilter();
      final Long changeFrom = settings.getChangeAfterFilter();
      final Date dateTo = settings.getDateBeforeFilter();
      final Long changeTo = settings.getChangeBeforeFilter();

      final SVNRevision revisionBefore;
      if (dateTo != null) {
        revisionBefore = SVNRevision.create(dateTo);
      }
      else if (changeTo != null) {
        revisionBefore = SVNRevision.create(changeTo.longValue());
      }
      else {
        revisionBefore = SVNRevision.create(myVcs.createRepository(url).getLatestRevision());
      }
      final SVNRevision revisionAfter;
      if (dateFrom != null) {
        revisionAfter = SVNRevision.create(dateFrom);
      }
      else if (changeFrom != null) {
        revisionAfter = SVNRevision.create(changeFrom.longValue());
      }
      else {
        revisionAfter = SVNRevision.create(1);
      }

      logger.doLog(SVNURL.parseURIEncoded(url), filterUrls, revisionBefore, revisionBefore, revisionAfter,
                   settings.STOP_ON_COPY, true, includeMergedRevisions, maxCount, null,
                   new ISVNLogEntryHandler() {
                     public void handleLogEntry(SVNLogEntry logEntry) {
                       if (myProject.isDisposed()) throw new ProcessCanceledException();
                       if (progress != null) {
                         progress.setText2(SvnBundle.message("progress.text2.processing.revision", logEntry.getRevision()));
                         progress.checkCanceled();
                       }
                       if (filterOutByDate && logEntry.getDate() == null) {
                         // do not add lists without info - this situation is possible for lists where there are paths that user has no rights to observe
                         return;
                       }
                       if (author == null || author.equalsIgnoreCase(logEntry.getAuthor())) {
                         resultConsumer.consume(logEntry);
                       }
                     }
                   });
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[] {
      new ChangeListColumn.ChangeListNumberColumn(SvnBundle.message("revision.title")),
      ChangeListColumn.NAME, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION
    };
  }

  private void refreshMergeInfo(final RootsAndBranches action) {
    if (myMergeInfoUpdatesListener == null) {
      myMergeInfoUpdatesListener = new MergeInfoUpdatesListener(myProject, myConnection);
    }
    myMergeInfoUpdatesListener.addPanel(action);
  }

  private static class ShowHideMergePanel extends ToggleAction {
    private final DecoratorManager myManager;
    private final ChangeListFilteringStrategy myStrategy;
    private boolean myIsSelected;
    private final static String ourKey = "MERGE_PANEL";

    public ShowHideMergePanel(final DecoratorManager manager, final ChangeListFilteringStrategy strategy) {
      myManager = manager;
      myStrategy = strategy;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setIcon(IconLoader.getIcon("/icons/ShowIntegratedFrom.png"));
      presentation.setText(SvnBundle.message("committed.changes.action.enable.merge.highlighting"));
      presentation.setDescription(SvnBundle.message("committed.changes.action.enable.merge.highlighting.description.text"));
    }

    public boolean isSelected(final AnActionEvent e) {
      return myIsSelected;
    }

    public void setSelected(final AnActionEvent e, final boolean state) {
      myIsSelected = state;
      if (state) {
        myManager.setFilteringStrategy(ourKey, myStrategy);
      } else {
        myManager.removeFilteringStrategy(ourKey);
      }
    }
  }

  @Nullable
  public VcsCommittedViewAuxiliary createActions(final DecoratorManager manager, @Nullable final RepositoryLocation location) {
    final RootsAndBranches rootsAndBranches = new RootsAndBranches(myProject, manager, location);
    refreshMergeInfo(rootsAndBranches);

    final DefaultActionGroup popup = new DefaultActionGroup(myVcs.getDisplayName(), true);
    popup.add(rootsAndBranches.getIntegrateAction());
    popup.add(rootsAndBranches.getUndoIntegrateAction());
    popup.add(new ConfigureBranchesAction());

    final ShowHideMergePanel action = new ShowHideMergePanel(manager, rootsAndBranches.getStrategy());

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

  public int getFormatVersion() {
    return VERSION_WITH_REPLACED_PATHS;
  }

  public void writeChangeList(final DataOutput dataStream, final SvnChangeList list) throws IOException {
    list.writeToStream(dataStream);
  }

  public SvnChangeList readChangeList(final RepositoryLocation location, final DataInput stream) throws IOException {
    final int version = getFormatVersion();
    return new SvnChangeList(myVcs, (SvnRepositoryLocation) location, stream,
                             VERSION_WITH_COPY_PATHS_ADDED <= version, VERSION_WITH_REPLACED_PATHS <= version);  
  }

  public boolean isMaxCountSupported() {
    return true;
  }

  public Collection<FilePath> getIncomingFiles(final RepositoryLocation location) {
    return null;
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
