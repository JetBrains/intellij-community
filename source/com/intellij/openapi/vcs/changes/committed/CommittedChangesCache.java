package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author yole
 */
public class CommittedChangesCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.CommittedChangesCache");
  @NonNls private static final String VCS_CACHE_PATH = "vcsCache";
  private Map<RepositoryLocation, ChangesCacheFile> myCacheFiles = new HashMap<RepositoryLocation, ChangesCacheFile>();
  private int myInitialCount = 500;
  private MessageBus myBus;

  public static final Topic<CommittedChangesListener> COMMITTED_TOPIC = new Topic<CommittedChangesListener>("committed changes updates",
                                                                                                            CommittedChangesListener.class);

  public static CommittedChangesCache getInstance(Project project) {
    return ServiceManager.getService(project, CommittedChangesCache.class);
  }

  private Project myProject;

  public CommittedChangesCache(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public int getInitialCount() {
    return myInitialCount;
  }

  public void setInitialCount(final int initialCount) {
    myInitialCount = initialCount;
  }

  public List<CommittedChangeList> getProjectChanges(final ChangeBrowserSettings settings, final int maxCount) throws VcsException {
    final VirtualFile[] files = ProjectLevelVcsManager.getInstance(myProject).getAllVersionedRoots();
    final LinkedHashSet<CommittedChangeList> result = new LinkedHashSet<CommittedChangeList>();
    for(VirtualFile file: files) {
      result.addAll(getChanges(settings, file, maxCount));
    }
    return new ArrayList<CommittedChangeList>(result);
  }

  public List<CommittedChangeList> getChanges(ChangeBrowserSettings settings, final VirtualFile file, final int maxCount)
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
      final CachingCommittedChangesProvider cachingProvider = (CachingCommittedChangesProvider)provider;
      if (canGetFromCache(cachingProvider, settings, location, maxCount)) {
        try {
          return getChangesWithCaching(cachingProvider, settings, location, maxCount);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    //noinspection unchecked
    return provider.getCommittedChanges(settings, location, maxCount);
  }

  private boolean canGetFromCache(final CachingCommittedChangesProvider cachingProvider, final ChangeBrowserSettings settings,
                                  final RepositoryLocation location, final int maxCount) {
    ChangesCacheFile cacheFile = getCacheFile(cachingProvider, location);
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
      if (cacheFile.isEmpty()) {
        return false;
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
          ChangesCacheFile cacheFile = getCacheFile((CachingCommittedChangesProvider) provider, location);
          result.add(cacheFile);
        }
      }
    }
    return result;
  }

  private List<CommittedChangeList> getChangesWithCaching(final CachingCommittedChangesProvider provider,
                                                          final ChangeBrowserSettings settings,
                                                          final RepositoryLocation location,
                                                          final int maxCount) throws VcsException, IOException {
    ChangesCacheFile cacheFile = getCacheFile(provider, location);
    if (cacheFile.isEmpty()) {
      List<CommittedChangeList> changes = provider.getCommittedChanges(provider.createDefaultSettings(), location, myInitialCount);
      // when initially initializing cache, assume all changelists are locally available
      cacheFile.writeChanges(changes, true); // this sorts changes in chronological order
      if (changes.size() < myInitialCount) {
        cacheFile.setHaveCompleteHistory(true);
      }
      if (canGetFromCache(provider, settings, location, maxCount)) {
        settings.filterChanges(changes);
        return trimToSize(changes, maxCount);
      }
      return provider.getCommittedChanges(settings, location, maxCount);
    }
    else {
      List<CommittedChangeList> changes = cacheFile.readChanges(settings, maxCount);
      final Date date = cacheFile.getLastCachedDate();
      final ChangeBrowserSettings defaultSettings = provider.createDefaultSettings();
      defaultSettings.setDateAfter(date);
      List<CommittedChangeList> newChanges = provider.getCommittedChanges(defaultSettings, location, 0);
      newChanges = cacheFile.writeChanges(newChanges, false);    // skip duplicates
      if (newChanges.size() > 0) {
        myBus.syncPublisher(COMMITTED_TOPIC).changesLoaded(location, newChanges);
      }
      settings.filterChanges(newChanges);
      changes.addAll(newChanges);
      return trimToSize(changes, maxCount);
    }
  }

  private static List<CommittedChangeList> trimToSize(final List<CommittedChangeList> changes, final int maxCount) {
    while(changes.size() > maxCount) {
      changes.remove(0);
    }
    return changes;
  }

  public List<CommittedChangeList> getIncomingChanges() {
    final List<CommittedChangeList> result = new ArrayList<CommittedChangeList>();
    final Collection<ChangesCacheFile> caches = getAllCaches();
    for(ChangesCacheFile cache: caches) {
      if (!cache.isEmpty()) {
        try {
          result.addAll(cache.loadIncomingChanges());
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return result;
  }

  public void processUpdatedFiles(final UpdatedFiles updatedFiles) {
    final Collection<ChangesCacheFile> caches = getAllCaches();
    for(ChangesCacheFile cache: caches) {
      try {
        cache.processUpdatedFiles(updatedFiles);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    myBus.syncPublisher(COMMITTED_TOPIC).updatedFilesProcessed();
  }

  private ChangesCacheFile getCacheFile(final CachingCommittedChangesProvider provider, final RepositoryLocation location) {
    ChangesCacheFile cacheFile = myCacheFiles.get(location);
    if (cacheFile == null) {
      cacheFile = new ChangesCacheFile(getCachePath(location), provider, location);
      myCacheFiles.put(location, cacheFile);
    }
    return cacheFile;
  }

  private File getCachePath(final RepositoryLocation location) {
    File file = new File(PathManager.getSystemPath(), VCS_CACHE_PATH);
    file = new File(file, myProject.getLocationHash());
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
}
