package com.intellij.openapi.vcs.changes.committed;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author yole
 */
@State(
  name="CommittedChangesCache",
  storages= {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class CommittedChangesCache implements PersistentStateComponent<CommittedChangesCache.State> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.CommittedChangesCache");
  @NonNls private static final String VCS_CACHE_PATH = "vcsCache";
  private Map<RepositoryLocation, ChangesCacheFile> myCacheFiles = new HashMap<RepositoryLocation, ChangesCacheFile>();
  private MessageBus myBus;
  private BackgroundTaskQueue myTaskQueue;
  private boolean myRefreshingIncomingChanges = false;
  private int myProjectChangesRefreshCount = 0;
  private int myPendingUpdateCount = 0;
  private State myState = new State();
  private ScheduledFuture myFuture;
  private List<CommittedChangeList> myCachedIncomingChanges;

  public static class State {
    private int myInitialCount = 500;
    private int myInitialDays = 90;
    private int myRefreshInterval = 30;
    private boolean myRefreshEnabled = true;

    public int getInitialCount() {
      return myInitialCount;
    }

    public void setInitialCount(final int initialCount) {
      myInitialCount = initialCount;
    }

    public int getInitialDays() {
      return myInitialDays;
    }

    public void setInitialDays(final int initialDays) {
      myInitialDays = initialDays;
    }

    public int getRefreshInterval() {
      return myRefreshInterval;
    }

    public void setRefreshInterval(final int refreshInterval) {
      myRefreshInterval = refreshInterval;
    }

    public boolean isRefreshEnabled() {
      return myRefreshEnabled;
    }

    public void setRefreshEnabled(final boolean refreshEnabled) {
      myRefreshEnabled = refreshEnabled;
    }
  }

  public static final Topic<CommittedChangesListener> COMMITTED_TOPIC = new Topic<CommittedChangesListener>("committed changes updates",
                                                                                                            CommittedChangesListener.class);

  public static CommittedChangesCache getInstance(Project project) {
    return project.getComponent(CommittedChangesCache.class);
  }

  private Project myProject;

  public CommittedChangesCache(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
    myTaskQueue = new BackgroundTaskQueue(project, VcsBundle.message("committed.changes.refresh.progress"));
  }

  public MessageBus getMessageBus() {
    return myBus;
  }

  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
    updateRefreshTimer();
  }

  @Nullable
  public CommittedChangesProvider getProviderForProject() {
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    List<AbstractVcs> vcsWithProviders = new ArrayList<AbstractVcs>();
    for(AbstractVcs vcs: vcss) {
      if (vcs.getCommittedChangesProvider() != null) {
        vcsWithProviders.add(vcs);
      }
    }
    assert vcsWithProviders.size() > 0;
    CommittedChangesProvider provider = null;
    if (vcsWithProviders.size() == 1) {
      provider = vcsWithProviders.get(0).getCommittedChangesProvider();
    }
    else {
      provider = new CompositeCommittedChangesProvider(myProject, vcsWithProviders.toArray(new AbstractVcs[vcsWithProviders.size()]));
    }
    return provider;
  }

  public boolean isMaxCountSupportedForProject() {
    for(AbstractVcs vcs: ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider instanceof CachingCommittedChangesProvider) {
        final CachingCommittedChangesProvider cachingProvider = (CachingCommittedChangesProvider)provider;
        if (!cachingProvider.isMaxCountSupported()) {
          return false;
        }
      }
    }
    return true;
  }

  public void getProjectChangesAsync(final ChangeBrowserSettings settings,
                                     final int maxCount,
                                     final boolean cacheOnly,
                                     final Consumer<List<CommittedChangeList>> consumer,
                                     final Consumer<List<VcsException>> errorConsumer) {
    myProjectChangesRefreshCount++;
    final Task.Backgroundable task = new Task.Backgroundable(myProject, VcsBundle.message("committed.changes.refresh.progress")) {
      private final LinkedHashSet<CommittedChangeList> myResult = new LinkedHashSet<CommittedChangeList>();
      private final List<VcsException> myExceptions = new ArrayList<VcsException>();

      public void run(final ProgressIndicator indicator) {
        final VirtualFile[] files = ProjectLevelVcsManager.getInstance(myProject).getAllVersionedRoots();
        for(VirtualFile file: files) {
          try {
            myResult.addAll(getChanges(settings, file, maxCount, cacheOnly));
          }
          catch (VcsException e) {
            myExceptions.add(e);
          }
        }
      }

      public void onSuccess() {
        myProjectChangesRefreshCount--;
        if (myExceptions.size() > 0) {
          errorConsumer.consume(myExceptions);
        }
        else {
          consumer.consume(new ArrayList<CommittedChangeList>(myResult));
        }
      }
    };
    myTaskQueue.run(task);
  }

  public boolean isRefreshingProjectChanges() {
    return myProjectChangesRefreshCount > 0;
  }

  public List<CommittedChangeList> getChanges(ChangeBrowserSettings settings, final VirtualFile file, final int maxCount,
                                              final boolean cacheOnly)
    throws VcsException {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    assert vcs != null;
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    if (provider == null) {
      return Collections.emptyList();
    }
    final RepositoryLocation location = provider.getLocationFor(file);
    if (location == null) {
      return Collections.emptyList();
    }
    if (settings instanceof CompositeCommittedChangesProvider.CompositeChangeBrowserSettings) {
      settings = ((CompositeCommittedChangesProvider.CompositeChangeBrowserSettings) settings).get(vcs);
    }
    if (provider instanceof CachingCommittedChangesProvider) {
      try {
        if (cacheOnly) {
          ChangesCacheFile cacheFile = getCacheFile(vcs, file, location);
          if (!cacheFile.isEmpty()) {
            return cacheFile.readChanges(settings, maxCount);
          }
          return Collections.emptyList();
        }
        else {
          if (canGetFromCache(vcs, settings, file, location, maxCount)) {
            return getChangesWithCaching(vcs, settings, file, location, maxCount);
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    //noinspection unchecked
    return provider.getCommittedChanges(settings, location, maxCount);
  }

  private boolean canGetFromCache(final AbstractVcs vcs, final ChangeBrowserSettings settings,
                                  final VirtualFile root, final RepositoryLocation location, final int maxCount) throws IOException {
    ChangesCacheFile cacheFile = getCacheFile(vcs, root, location);
    if (cacheFile.isEmpty()) {
      return true;   // we'll initialize the cache and check again after that
    }
    if (settings.USE_DATE_BEFORE_FILTER && !settings.USE_DATE_AFTER_FILTER) {
      return cacheFile.hasCompleteHistory();
    }
    if (settings.USE_CHANGE_BEFORE_FILTER && !settings.USE_CHANGE_AFTER_FILTER) {
      return cacheFile.hasCompleteHistory();
    }

    boolean hasDateFilter = settings.USE_DATE_AFTER_FILTER || settings.USE_DATE_BEFORE_FILTER || settings.USE_CHANGE_AFTER_FILTER || settings.USE_CHANGE_BEFORE_FILTER;
    boolean hasNonDateFilter = settings.isNonDateFilterSpecified();
    if (!hasDateFilter && hasNonDateFilter) {
      return cacheFile.hasCompleteHistory();
    }
    if (settings.USE_DATE_AFTER_FILTER && settings.getDateAfter().getTime() < cacheFile.getFirstCachedDate().getTime()) {
      return cacheFile.hasCompleteHistory();
    }
    if (settings.USE_CHANGE_AFTER_FILTER && settings.getChangeAfterFilter().longValue() < cacheFile.getFirstCachedChangelist()) {
      return cacheFile.hasCompleteHistory();
    }
    return true;
  }

  public boolean hasCachesForAllRoots() {
    Collection<ChangesCacheFile> caches = getAllCaches();
    for(ChangesCacheFile cacheFile: caches) {
      try {
        if (cacheFile.isEmpty()) {
          return false;
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return true;
  }

  private Collection<ChangesCacheFile> getAllCaches() {
    Collection<ChangesCacheFile> result = new ArrayList<ChangesCacheFile>();
    final VirtualFile[] files = ProjectLevelVcsManager.getInstance(myProject).getAllVersionedRoots();
    for(VirtualFile file: files) {
      final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      assert vcs != null;
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider instanceof CachingCommittedChangesProvider) {
        final RepositoryLocation location = provider.getLocationFor(file);
        if (location != null) {
          ChangesCacheFile cacheFile = getCacheFile(vcs, file, location);
          result.add(cacheFile);
        }
      }
    }
    return result;
  }

  private List<CommittedChangeList> getChangesWithCaching(final AbstractVcs vcs,
                                                          final ChangeBrowserSettings settings,
                                                          final VirtualFile root,
                                                          final RepositoryLocation location,
                                                          final int maxCount) throws VcsException, IOException {
    ChangesCacheFile cacheFile = getCacheFile(vcs, root, location);
    if (cacheFile.isEmpty()) {
      List<CommittedChangeList> changes = initCache(cacheFile);
      if (canGetFromCache(vcs, settings, root, location, maxCount)) {
        settings.filterChanges(changes);
        return trimToSize(changes, maxCount);
      }
      //noinspection unchecked
      return cacheFile.getProvider().getCommittedChanges(settings, location, maxCount);
    }
    else {
      List<CommittedChangeList> changes = cacheFile.readChanges(settings, maxCount);
      List<CommittedChangeList> newChanges = refreshCache(cacheFile);
      settings.filterChanges(newChanges);
      changes.addAll(newChanges);
      return trimToSize(changes, maxCount);
    }
  }

  public void refreshAllCaches() throws IOException, VcsException {
    final Collection<ChangesCacheFile> files = getAllCaches();
    for(ChangesCacheFile file: files) {
      if (file.isEmpty()) {
        initCache(file);
      }
      else {
        refreshCache(file);
      }
    }
  }

  private List<CommittedChangeList> initCache(final ChangesCacheFile cacheFile) throws VcsException, IOException {
    LOG.info("Initializing cache for " + cacheFile.getLocation());
    final CachingCommittedChangesProvider provider = cacheFile.getProvider();
    final RepositoryLocation location = cacheFile.getLocation();
    final ChangeBrowserSettings settings = provider.createDefaultSettings();
    int maxCount = 0;
    if (isMaxCountSupportedForProject()) {
      maxCount = myState.getInitialCount();
    }
    else {
      settings.USE_DATE_AFTER_FILTER = true;
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.DAY_OF_YEAR, -myState.getInitialDays());
      settings.setDateAfter(calendar.getTime());
    }
    //noinspection unchecked
    List<CommittedChangeList> changes = provider.getCommittedChanges(settings, location, maxCount);
    // when initially initializing cache, assume all changelists are locally available
    cacheFile.writeChanges(changes); // this sorts changes in chronological order
    if (maxCount > 0 && changes.size() < myState.getInitialCount()) {
      cacheFile.setHaveCompleteHistory(true);
    }
    if (changes.size() > 0) {
      myBus.syncPublisher(COMMITTED_TOPIC).changesLoaded(location, changes);
    }
    return changes;
  }

  private List<CommittedChangeList> refreshCache(final ChangesCacheFile cacheFile) throws VcsException, IOException {
    final CachingCommittedChangesProvider provider = cacheFile.getProvider();
    final RepositoryLocation location = cacheFile.getLocation();
    final ChangeBrowserSettings defaultSettings = provider.createDefaultSettings();
    if (cacheFile.getProvider().refreshCacheByNumber()) {
      final long number = cacheFile.getLastCachedChangelist();
      LOG.info("Refreshing cache for " + cacheFile.getLocation() + " since #" + number);
      defaultSettings.CHANGE_AFTER = Long.toString(number);
      defaultSettings.USE_CHANGE_AFTER_FILTER = true;
    }
    else {
      final Date date = cacheFile.getLastCachedDate();
      LOG.info("Refreshing cache for " + cacheFile.getLocation() + " since " + date);
      defaultSettings.setDateAfter(date);
      defaultSettings.USE_DATE_AFTER_FILTER = true;
    }
    List<CommittedChangeList> newChanges = provider.getCommittedChanges(defaultSettings, location, 0);
    LOG.info("Loaded " + newChanges.size() + " new changelists");
    newChanges = cacheFile.writeChanges(newChanges);    // skip duplicates
    if (newChanges.size() > 0) {
      myBus.syncPublisher(COMMITTED_TOPIC).changesLoaded(location, newChanges);
    }
    return newChanges;
  }

  private static List<CommittedChangeList> trimToSize(final List<CommittedChangeList> changes, final int maxCount) {
    if (maxCount > 0) {
      while(changes.size() > maxCount) {
        changes.remove(0);
      }
    }
    return changes;
  }

  private List<CommittedChangeList> getIncomingChanges() {
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    final Collection<ChangesCacheFile> caches = getAllCaches();
    for(ChangesCacheFile cache: caches) {
      try {
        if (!cache.isEmpty()) {
          LOG.info("Loading incoming changes for " + cache.getLocation());
          result.addAll(cache.loadIncomingChanges());
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return result;
  }

  public void loadIncomingChangesAsync(final Consumer<List<CommittedChangeList>> consumer) {
    final Task.Backgroundable task = new Task.Backgroundable(myProject, "Loading incoming changes") {
      public void run(final ProgressIndicator indicator) {
        myCachedIncomingChanges = getIncomingChanges();
        consumer.consume(myCachedIncomingChanges);
      }
    };
    myTaskQueue.run(task);
  }

  public List<CommittedChangeList> getCachedIncomingChanges() {
    return myCachedIncomingChanges;
  }

  public void processUpdatedFiles(final UpdatedFiles updatedFiles) {
    LOG.info("Processing updated files");
    final Collection<ChangesCacheFile> caches = getAllCaches();
    for(final ChangesCacheFile cache: caches) {
      myPendingUpdateCount++;
      final Task.Backgroundable task = new Task.Backgroundable(myProject, "Processing updated files") {
        public void run(final ProgressIndicator indicator) {
          try {
            if (cache.isEmpty()) {
              pendingUpdateProcessed();
              return;
            }
            LOG.info("Processing updated files in " + cache.getLocation());
            boolean needRefresh = cache.processUpdatedFiles(updatedFiles);
            if (needRefresh) {
              LOG.info("Found unaccounted files, requesting refresh");
              processUpdatedFilesAfterRefresh(cache, updatedFiles);
            }
            else {
              pendingUpdateProcessed();
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      };
      myTaskQueue.run(task);
    }
  }

  private void pendingUpdateProcessed() {
    myPendingUpdateCount--;
    if (myPendingUpdateCount == 0) {
      notifyIncomingChangesUpdated();
    }
  }

  private void processUpdatedFilesAfterRefresh(final ChangesCacheFile cache, final UpdatedFiles updatedFiles) {
    refreshCacheAsync(cache, false, new Consumer<List<CommittedChangeList>>() {
      public void consume(final List<CommittedChangeList> committedChangeLists) {
        try {
          LOG.info("Processing updated files after refresh in " + cache.getLocation());
          boolean result = true;
          if (committedChangeLists.size() > 0) {
            // received some new changelists, try to process updated files again
            result = cache.processUpdatedFiles(updatedFiles);
          }
          LOG.info(result ? "Still have unaccounted files" : "No more unaccounted files");
          // for svn, we won't get exact revision numbers in updatedFiles, so we have to double-check by
          // checking revisions we have locally
          if (result) {
            cache.refreshIncomingChanges();
          }
          pendingUpdateProcessed();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  private void notifyIncomingChangesUpdated() {
    myBus.syncPublisher(COMMITTED_TOPIC).incomingChangesUpdated();
  }

  public boolean isRefreshingIncomingChanges() {
    return myRefreshingIncomingChanges;
  }

  public boolean refreshIncomingChanges() {
    boolean hasChanges = false;
    final Collection<ChangesCacheFile> caches = getAllCaches();
    for(ChangesCacheFile file: caches) {
      try {
        if (file.isEmpty()) {
          continue;
        }
        LOG.info("Refreshing incoming changes for " + file.getLocation());
        boolean changesForCache = file.refreshIncomingChanges();
        hasChanges |= changesForCache;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return hasChanges;
  }

  public void refreshIncomingChangesAsync() {
    LOG.info("Refreshing incoming changes in background");
    myRefreshingIncomingChanges = true;
    final Task.Backgroundable task = new Task.Backgroundable(myProject, "Refreshing incoming changes") {
      private boolean myAnyChanges = false;

      public void run(final ProgressIndicator indicator) {
        myAnyChanges = refreshIncomingChanges();
      }

      public void onSuccess() {
        myRefreshingIncomingChanges = false;
        if (myAnyChanges) {
          notifyIncomingChangesUpdated();
        }
      }
    };
    myTaskQueue.run(task);    
  }

  public void refreshAllCachesAsync(final boolean initIfEmpty) {
    final Collection<ChangesCacheFile> files = getAllCaches();
    for(ChangesCacheFile file: files) {
      refreshCacheAsync(file, initIfEmpty, null);
    }
  }

  private void refreshCacheAsync(final ChangesCacheFile cache, final boolean initIfEmpty,
                                 @Nullable final Consumer<List<CommittedChangeList>> postRunnable) {
    try {
      if (!initIfEmpty && cache.isEmpty()) {
        return;
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    final Task.Backgroundable task = new Task.Backgroundable(myProject, VcsBundle.message("committed.changes.refresh.progress")) {
      public void run(final ProgressIndicator indicator) {
        try {
          final List<CommittedChangeList> list;
          if (initIfEmpty && cache.isEmpty()) {
            list = initCache(cache);
          }
          else {
            list = refreshCache(cache);
          }
          if (postRunnable != null) {
            postRunnable.consume(list);
          }
        }
        catch(ProcessCanceledException ex) {
          // ignore
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    };
    myTaskQueue.run(task);
  }

  public ChangesCacheFile getCacheFile(AbstractVcs vcs, VirtualFile root, RepositoryLocation location) {
    ChangesCacheFile cacheFile = myCacheFiles.get(location);
    if (cacheFile == null) {
      cacheFile = new ChangesCacheFile(myProject, getCachePath(location), vcs, root, location);
      myCacheFiles.put(location, cacheFile);
    }
    return cacheFile;
  }

  public File getCacheBasePath() {
    File file = new File(PathManager.getSystemPath(), VCS_CACHE_PATH);
    file = new File(file, myProject.getLocationHash());
    return file;
  }

  private File getCachePath(final RepositoryLocation location) {
    File file = getCacheBasePath();
    file.mkdirs();
    String s = location.toString();
    try {
      final byte[] bytes = MessageDigest.getInstance("MD5").digest(CharsetToolkit.getUtf8Bytes(s));
      StringBuilder result = new StringBuilder();
      for (byte aByte : bytes) {
        result.append(String.format("%02x", aByte));
      }
      return new File(file, result.toString());
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateRefreshTimer() {
    if (myFuture != null) {
      myFuture.cancel(false);
      myFuture = null;
    }
    if (myState.isRefreshEnabled()) {
      myFuture = JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
        public void run() {
          refreshAllCachesAsync(false);
        }
      }, myState.getRefreshInterval()*60, myState.getRefreshInterval()*60, TimeUnit.SECONDS);
    }
  }
}
