package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
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

  public static CommittedChangesCache getInstance(Project project) {
    return ServiceManager.getService(project, CommittedChangesCache.class);
  }

  private Project myProject;

  public CommittedChangesCache(final Project project) {
    myProject = project;
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
      return getChangesWithCaching((CachingCommittedChangesProvider) provider, settings, location, maxCount);
    }
    //noinspection unchecked
    return provider.getCommittedChanges(settings, location, maxCount);
  }

  private List<CommittedChangeList> getChangesWithCaching(final CachingCommittedChangesProvider provider,
                                                          final ChangeBrowserSettings settings,
                                                          final RepositoryLocation location,
                                                          final int maxCount) throws VcsException {
    ChangesCacheFile cacheFile = new ChangesCacheFile(getCachePath(location), provider, location);
    if (cacheFile.isEmpty()) {
      List<CommittedChangeList> changes = provider.getCommittedChanges(provider.createDefaultSettings(), location, 0);
      try {
        cacheFile.writeChanges(changes);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      settings.filterChanges(changes);
      return changes;
    }
    else {
      try {
        List<CommittedChangeList> changes = cacheFile.readChanges(settings);
        final Date date = cacheFile.getLastCachedDate();
        final ChangeBrowserSettings defaultSettings = provider.createDefaultSettings();
        defaultSettings.setDateAfter(date);
        List<CommittedChangeList> newChanges = provider.getCommittedChanges(defaultSettings, location, 0);
        newChanges = cacheFile.writeChanges(newChanges);    // skip duplicates
        settings.filterChanges(newChanges);
        changes.addAll(newChanges);
        return changes;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return provider.getCommittedChanges(settings, location, maxCount);
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
