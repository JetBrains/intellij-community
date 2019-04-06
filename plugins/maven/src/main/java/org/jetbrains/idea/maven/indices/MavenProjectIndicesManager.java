/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.onlinecompletion.DependencyCompletionProvider;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.IndexBasedSearchService;
import org.jetbrains.idea.maven.onlinecompletion.LocalCompletionSearch;
import org.jetbrains.idea.maven.onlinecompletion.central.MavenCentralOnlineSearch;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class MavenProjectIndicesManager extends MavenSimpleProjectComponent implements BaseComponent {
  private volatile List<MavenIndex> myProjectIndices = new ArrayList<>();
  private volatile boolean offlineIndexes = false;
  private volatile DependencySearchService mySearchService = new DependencySearchService(Collections.EMPTY_LIST);
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
  }

  @Override
  public void initComponent() {
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

  public void scheduleUpdateRepositoryList() {
    scheduleUpdateIndicesList();
  }
  private void scheduleUpdateIndicesList() {
    scheduleUpdateIndicesList(null);
  }

  public void scheduleUpdateIndicesList(@Nullable final Consumer<List<MavenIndex>> consumer) {
    myUpdateQueue.queue(new Update(MavenProjectIndicesManager.this) {
      @Override
      public void run() {
        Set<Pair<String, String>> remoteRepositoriesIdsAndUrls;
        File localRepository;


        remoteRepositoriesIdsAndUrls = ReadAction.compute(() -> myProject.isDisposed() ? null : collectRemoteRepositoriesIdsAndUrls());
        localRepository = ReadAction.compute(() -> myProject.isDisposed() ? null : getLocalRepository());
        if (remoteRepositoriesIdsAndUrls == null || localRepository == null) return;
        Set<DependencyCompletionProvider> providers = new HashSet<>();
        providers.add(new LocalCompletionSearch(localRepository));
        List<MavenIndex> newIndices = new ArrayList<>();

        Iterator<Pair<String, String>> iterator = remoteRepositoriesIdsAndUrls.iterator();

        while (iterator.hasNext()) {
          Pair<String, String> pair = iterator.next();
          //todo - need stub server
          if (pair.second.contains("repo.maven.apache.org/maven2") || "central".equals(pair.first)) {
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              providers.add(new MavenCentralOnlineSearch());
            }
            iterator.remove();
          }
        }

        List<MavenIndex> offlineIndices =
          MavenIndicesManager.getInstance().ensureIndicesExist(myProject, remoteRepositoriesIdsAndUrls);

        for (MavenSearchIndex index : offlineIndices) {
          if (index instanceof MavenIndex) {
            providers.add(new IndexBasedSearchService((MavenIndex)index));
          }
        }
        newIndices.addAll(offlineIndices);
        synchronized (this) {
          offlineIndexes = !remoteRepositoriesIdsAndUrls.isEmpty();
          myProjectIndices = newIndices;
          mySearchService = new DependencySearchService(new ArrayList<>(providers));
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

  @Deprecated
  /* @deprecated use getSearchService */
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


  public synchronized DependencySearchService getSearchService() {
    return mySearchService;
  }

  @Deprecated
  /** @deprecated use {@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findGroupCandidates} or{@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findByTemplate} instead**/
  public Set<String> getGroupIds() {
    return getGroupIds("");
  }

  @Deprecated
  /** @deprecated use {@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findGroupCandidates} or{@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findByTemplate} instead**/
  public Set<String> getGroupIds(String pattern) {
    pattern = pattern == null ? "" : pattern;
    //todo fix
    return getSearchService().findGroupCandidates(new MavenDependencyCompletionItem(pattern))
      .stream().map(d -> d.getArtifactId())
      .collect(
        Collectors.toSet());
  }

  @Deprecated
  /** @deprecated use {@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findArtifactCandidates} or{@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findByTemplate} instead**/
  public Set<String> getArtifactIds(String groupId) {
    ProgressIndicatorProvider.checkCanceled();
    return getSearchService().findArtifactCandidates(new MavenDependencyCompletionItem(groupId)).stream().map(d -> d.getArtifactId())
      .collect(
        Collectors.toSet());
  }

  /**
   * @deprecated use {@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findAllVersions or{@link org.jetbrains.idea.maven.onlinecompletion.DependencySearchService#findByTemplate} instead
   **/
  public Set<String> getVersions(String groupId, String artifactId) {
    return getSearchService().findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null)).stream()
      .map(d -> d.getArtifactId()).collect(
        Collectors.toSet());
  }

  @Deprecated
  public boolean hasGroupId(String groupId) {
    return !getSearchService().findGroupCandidates(new MavenDependencyCompletionItem(groupId)).isEmpty();
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
    return !getSearchService().findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null), SearchParameters.DEFAULT.withFlag(SearchParameters.Flags.FULL_RESOLVE)).isEmpty();
  }

  @Deprecated
  public boolean hasVersion(String groupId, String artifactId, String version) {
    return getSearchService().findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null)).stream().anyMatch(
      s -> version.equals(s.getVersion())
    );
  }

  public Set<MavenArtifactInfo> search(Query query, int maxResult) {
    //TODO
    return Collections.emptySet();
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
