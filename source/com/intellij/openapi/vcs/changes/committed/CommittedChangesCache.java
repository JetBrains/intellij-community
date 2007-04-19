package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * @author yole
 */
public class CommittedChangesCache {
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
    //noinspection unchecked
    return provider.getCommittedChanges(settings, location, maxCount);
  }
}
