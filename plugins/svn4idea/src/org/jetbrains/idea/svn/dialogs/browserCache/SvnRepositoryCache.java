package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.SoftHashMap;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SvnRepositoryCache {
  private final Map<String, List<SVNDirEntry>> myMap;
  private final Map<String, SVNErrorMessage> myErrorsMap;

  public static SvnRepositoryCache getInstance() {
    return ServiceManager.getService(SvnRepositoryCache.class);
  }
  
  private SvnRepositoryCache() {
    myMap = new SoftHashMap<String, List<SVNDirEntry>>();
    myErrorsMap = new SoftHashMap<String, SVNErrorMessage>();
  }

  @Nullable
  public List<SVNDirEntry> getChildren(final String parent) {
    return myMap.get(parent);
  }

  @Nullable
  public SVNErrorMessage getError(final String parent) {
    return myErrorsMap.get(parent);
  }

  public void put(final String parent, final SVNErrorMessage error) {
    myMap.remove(parent);
    myErrorsMap.put(parent, error);
  }

  public void put(final String parent, List<SVNDirEntry> children) {
    myErrorsMap.remove(parent);
    myMap.put(parent, children);
  }

  public void remove(final String parent) {
    myErrorsMap.remove(parent);
    myMap.remove(parent);
  }

  public void clear(final String repositoryRootUrl) {
    for (Iterator<Map.Entry<String, List<SVNDirEntry>>> iterator = myMap.entrySet().iterator(); iterator.hasNext();) {
      final Map.Entry<String, List<SVNDirEntry>> entry = iterator.next();
      if (entry.getKey().startsWith(repositoryRootUrl)) {
        iterator.remove();
      }
    }
  }
}
