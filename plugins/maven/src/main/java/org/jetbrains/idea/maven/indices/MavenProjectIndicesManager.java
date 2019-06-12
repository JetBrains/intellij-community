// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.onlinecompletion.DependencyCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.IndexBasedCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.OfflineSearchService;
import org.jetbrains.idea.maven.onlinecompletion.ProjectModulesCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class MavenProjectIndicesManager extends MavenSimpleProjectComponent {
  private volatile List<MavenIndex> myProjectIndices = new ArrayList<>();
  private volatile boolean offlineIndexes = false;
  private volatile OfflineSearchService myOfflineSearchService;
  private volatile DependencySearchService myDependencySearchService;
  private final MergingUpdateQueue myUpdateQueue;

  public boolean hasOfflineIndexes() {
    return offlineIndexes;
  }

  public static MavenProjectIndicesManager getInstance(Project p) {
    return p.getComponent(MavenProjectIndicesManager.class);
  }

  public MavenProjectIndicesManager(Project project) {
    super(project);
    myUpdateQueue = new MavenMergingUpdateQueue(getClass().getSimpleName(), 1000, true, project);
    myOfflineSearchService = new OfflineSearchService(project, Collections.emptyList());
    myDependencySearchService = new DependencySearchService(project, myOfflineSearchService);

    if (!isNormalProject()) return;
    doInit();
  }

  public void doInit() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      scheduleUpdateIndicesList();
    }

    getMavenProjectManager().addManagerListener(new MavenProjectsManager.Listener() {
      @Override
      public void activated() {
        scheduleUpdateIndicesList();
      }
    });

    getMavenProjectManager().addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
        scheduleUpdateIndicesList();
      }

      @Override
      public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject) {
        scheduleUpdateIndicesList();
      }
    });
  }

  private void scheduleUpdateIndicesList() {
    scheduleUpdateIndicesList(null);
  }

  public void scheduleUpdateIndicesList(@Nullable final Consumer<? super List<MavenIndex>> consumer) {
    myUpdateQueue.queue(new Update(this) {
      @Override
      public void run() {
        Set<Pair<String, String>> remoteRepositoriesIdsAndUrls;
        File localRepository;


        MavenIndicesManager indicesManager = MavenIndicesManager.getInstance();
        remoteRepositoriesIdsAndUrls = ReadAction.compute(() -> myProject.isDisposed() ? null : collectRemoteRepositoriesIdsAndUrls());
        localRepository = ReadAction.compute(() -> myProject.isDisposed() ? null : getLocalRepository());
        if (remoteRepositoriesIdsAndUrls == null || localRepository == null) return;
        Set<DependencyCompletionProvider> providers = new HashSet<>();
        MavenIndex localIndex = indicesManager.createIndexForLocalRepo(myProject, localRepository);
        if (localIndex != null) {
          providers.add(new IndexBasedCompletionProvider(localIndex));
        } else {
          MavenLog.LOG.warn("Cannot create local maven index for " + localRepository);
        }
        providers.add(new ProjectModulesCompletionProvider(myProject));


        List<MavenIndex> offlineIndices =
          MavenIndicesManager.getInstance().ensureIndicesExist(myProject, remoteRepositoriesIdsAndUrls);

        for (MavenSearchIndex index : offlineIndices) {
          if (index instanceof MavenIndex) {
            providers.add(new IndexBasedCompletionProvider((MavenIndex)index));
          }
        }

        List<MavenIndex> newIndices = new ArrayList<>(offlineIndices);
        if (localIndex != null) {
          newIndices.add(localIndex);
        }
        synchronized (this) {
          offlineIndexes = !remoteRepositoriesIdsAndUrls.isEmpty();
          myProjectIndices = newIndices;
          myOfflineSearchService = new OfflineSearchService(myProject, new ArrayList<>(providers));
          myDependencySearchService = new DependencySearchService(myProject, myOfflineSearchService);
        }

        if (consumer != null) {
          consumer.consume(myProjectIndices);
        }
      }
    });
  }

  private File getLocalRepository() {
    return MavenProjectsManager.getInstance(myProject).getLocalRepository();
  }

  private Set<Pair<String, String>> collectRemoteRepositoriesIdsAndUrls() {
    Set<Pair<String, String>> result = new THashSet<>();
    Set<MavenRemoteRepository> remoteRepositories = new HashSet<>(getMavenProjectManager().getRemoteRepositories());
    for (MavenRepositoryProvider repositoryProvider : MavenRepositoryProvider.EP_NAME.getExtensions()) {
      ContainerUtil.addAll(remoteRepositories, repositoryProvider.getRemoteRepositories(myProject));
    }
    for (MavenRemoteRepository each : remoteRepositories) {
      String id = each.getId();
      String url = each.getUrl();

      result.add(Pair.create(id, url));
    }
    return result;
  }

  /**
   * @deprecated use {@link #getOfflineSearchService()}
   */
  @Deprecated
  public List<MavenIndex> getIndices() {
    return new ArrayList<>(myProjectIndices);
  }

  public void scheduleUpdateAll() {
    MavenIndicesManager.getInstance().scheduleUpdate(myProject, myProjectIndices);
  }

  public void scheduleUpdate(List<MavenIndex> indices) {
    MavenIndicesManager.getInstance().scheduleUpdate(myProject, indices);
  }

  public MavenIndicesManager.IndexUpdatingState getUpdatingState(MavenSearchIndex index) {
    return MavenIndicesManager.getInstance().getUpdatingState(index);
  }

  private MavenProjectsManager getMavenProjectManager() {
    return MavenProjectsManager.getInstance(myProject);
  }


  public synchronized OfflineSearchService getOfflineSearchService() {
    return myOfflineSearchService;
  }

  public synchronized DependencySearchService getDependencySearchService() {
    return myDependencySearchService;
  }

  /**
   * @deprecated use {@link OfflineSearchService#findGroupCandidates} or{@link OfflineSearchService#findByTemplate} instead
   **/
  @Deprecated
  public Set<String> getGroupIds() {
    return getGroupIds("");
  }

  /**
   * @deprecated use {@link OfflineSearchService#findGroupCandidates} or{@link OfflineSearchService#findByTemplate} instead
   **/
  @Deprecated
  public Set<String> getGroupIds(String pattern) {
    pattern = pattern == null ? "" : pattern;
    //todo fix
    return getOfflineSearchService().findGroupCandidates(new MavenDependencyCompletionItem(pattern))
      .stream().map(d -> d.getArtifactId())
      .collect(
        Collectors.toSet());
  }

  /**
   * @deprecated use {@link OfflineSearchService#findArtifactCandidates} or{@link OfflineSearchService#findByTemplate} instead
   **/
  @Deprecated
  public Set<String> getArtifactIds(String groupId) {
    ProgressIndicatorProvider.checkCanceled();
    return getOfflineSearchService().findArtifactCandidates(new MavenDependencyCompletionItem(groupId)).stream().map(d -> d.getArtifactId())
      .collect(
        Collectors.toSet());
  }

  /**
   * @deprecated use {@link OfflineSearchService#findAllVersions or{@link OfflineSearchService#findByTemplate} instead
   **/
  @Deprecated
  public Set<String> getVersions(String groupId, String artifactId) {
    ProgressIndicatorProvider.checkCanceled();
    return getOfflineSearchService().findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null)).stream()
      .map(d -> d.getVersion()).collect(
        Collectors.toSet());
  }

  @Deprecated
  public boolean hasGroupId(String groupId) {
    ProgressIndicatorProvider.checkCanceled();
    if (hasProjectGroupId(groupId)) return true;
    return getOfflineSearchService().findGroupCandidates(new MavenDependencyCompletionItem(groupId)).stream()
      .anyMatch(p -> StringUtil.equals(groupId, p.getGroupId()));
  }

  private boolean checkLocalRepository(String groupId, String artifactId, String version) {
    if (StringUtil.isEmpty(groupId)) return false;

    String relPath = groupId.replace('.', '/');

    if (artifactId != null) {
      relPath += "/" + artifactId;
      if (version != null) {
        relPath += "/" + version + "/" + artifactId + "-" + version + ".pom";
      }
    }
    File file = new File(getLocalRepository(), relPath);

    return file.exists();
  }

  @Deprecated
  public boolean hasArtifactId(String groupId, String artifactId) {
    if (hasProjectArtifactId(groupId, artifactId)) return true;
    return !getOfflineSearchService().findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null),
                                                      SearchParameters.DEFAULT).isEmpty();
  }

  @Deprecated
  public boolean hasVersion(String groupId, String artifactId, String version) {
    if (hasProjectVersion(groupId, artifactId, version)) return true;
    return getOfflineSearchService().findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null)).stream().anyMatch(
      s -> version.equals(s.getVersion())
    );
  }

  private Set<String> getProjectGroupIds() {
    Set<String> result = new THashSet<>();
    for (MavenId each : getProjectsIds()) {
      result.add(each.getGroupId());
    }
    return result;
  }

  private Set<String> getProjectArtifactIds(String groupId) {
    Set<String> result = new THashSet<>();
    for (MavenId each : getProjectsIds()) {
      if (groupId.equals(each.getGroupId())) {
        result.add(each.getArtifactId());
      }
    }
    return result;
  }

  private Set<String> getProjectVersions(String groupId, String artifactId) {
    Set<String> result = new THashSet<>();
    for (MavenId each : getProjectsIds()) {
      if (groupId.equals(each.getGroupId()) && artifactId.equals(each.getArtifactId())) {
        result.add(each.getVersion());
      }
    }
    return result;
  }

  private boolean hasProjectGroupId(String groupId) {
    return getProjectGroupIds().contains(groupId);
  }

  private boolean hasProjectArtifactId(String groupId, String artifactId) {
    return getProjectArtifactIds(groupId).contains(artifactId);
  }

  private boolean hasProjectVersion(String groupId, String artifactId, String version) {
    return getProjectVersions(groupId, artifactId).contains(version);
  }

  private Set<MavenId> getProjectsIds() {
    Set<MavenId> result = new THashSet<>();
    for (MavenProject each : MavenProjectsManager.getInstance(myProject).getProjects()) {
      result.add(each.getMavenId());
    }
    return result;
  }
}
