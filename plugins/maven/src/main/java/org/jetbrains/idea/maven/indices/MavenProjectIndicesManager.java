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
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.apache.lucene.search.Query;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.SimpleProjectComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenProjectIndicesManager extends SimpleProjectComponent {
  private volatile List<MavenIndex> myProjectIndices = new ArrayList<MavenIndex>();
  private final MergingUpdateQueue myUpdateQueue;

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
      public void activated() {
        scheduleUpdateIndicesList();
      }

      public void scheduledImportsChanged() {
      }
    });

    getMavenProjectManager().addProjectsTreeListener(new MavenProjectsTree.ListenerAdapter() {
      @Override
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted, Object message) {
        scheduleUpdateIndicesList();
      }

      @Override
      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject,
                                  Object message) {
        scheduleUpdateIndicesList();
      }
    });
  }

  private void scheduleUpdateIndicesList() {
    myUpdateQueue.queue(new Update(MavenProjectIndicesManager.this) {
      public void run() {
        List<MavenIndex> newIndices = MavenIndicesManager.getInstance().ensureIndicesExist(
          myProject, getLocalRepository(), collectRemoteRepositoriesIdsAndUrls());
        myProjectIndices = newIndices;
      }
    });
  }

  private File getLocalRepository() {
    return MavenProjectsManager.getInstance(myProject).getLocalRepository();
  }

  private Set<Pair<String, String>> collectRemoteRepositoriesIdsAndUrls() {
    Set<Pair<String, String>> result = new THashSet<Pair<String, String>>();
    for (MavenRemoteRepository each : getMavenProjectManager().getRemoteRepositories()) {
      String id = each.getId();
      String url = each.getUrl();
      if (id == null || url == null) continue;
      result.add(Pair.create(id, url));
    }
    return result;
  }

  public List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myProjectIndices);
  }

  public void scheduleUpdateAll() {
    MavenIndicesManager.getInstance().scheduleUpdate(myProject, myProjectIndices);
  }

  public void scheduleUpdate(List<MavenIndex> indices) {
    MavenIndicesManager.getInstance().scheduleUpdate(myProject, indices);
  }

  public MavenIndicesManager.IndexUpdatingState getUpdatingState(MavenIndex index) {
    return MavenIndicesManager.getInstance().getUpdatingState(index);
  }

  private MavenProjectsManager getMavenProjectManager() {
    return MavenProjectsManager.getInstance(myProject);
  }

  public Set<String> getGroupIds() {
    Set<String> result = getProjectGroupIds();
    for (MavenIndex each : myProjectIndices) {
      result.addAll(each.getGroupIds());
    }
    return result;
  }

  public Set<String> getArtifactIds(String groupId) {
    Set<String> result = getProjectArtifactIds(groupId);
    for (MavenIndex each : myProjectIndices) {
      result.addAll(each.getArtifactIds(groupId));
    }
    return result;
  }

  public Set<String> getVersions(String groupId, String artifactId) {
    Set<String> result = getProjectVersions(groupId, artifactId);
    for (MavenIndex each : myProjectIndices) {
      result.addAll(each.getVersions(groupId, artifactId));
    }
    return result;
  }

  public boolean hasGroupId(String groupId) {
    if (hasProjectGroupId(groupId)) return true;
    for (MavenIndex each : myProjectIndices) {
      if (each.hasGroupId(groupId)) return true;
    }
    return checkLocalRepository(groupId, null, null);
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

  public boolean hasArtifactId(String groupId, String artifactId) {
    if (hasProjectArtifactId(groupId, artifactId)) return true;
    for (MavenIndex each : myProjectIndices) {
      if (each.hasArtifactId(groupId, artifactId)) return true;
    }
    return checkLocalRepository(groupId, artifactId, null);
  }

  public boolean hasVersion(String groupId, String artifactId, String version) {
    if (hasProjectVersion(groupId, artifactId, version)) return true;
    for (MavenIndex each : myProjectIndices) {
      if (each.hasVersion(groupId, artifactId, version)) return true;
    }
    return checkLocalRepository(groupId, artifactId, version);
  }

  public Set<MavenArtifactInfo> search(Query query, int maxResult) {
    Set<MavenArtifactInfo> result = new THashSet<MavenArtifactInfo>();

    for (MavenIndex each : myProjectIndices) {
      int remained = maxResult - result.size();
      if (remained <= 0) break;
      result.addAll(each.search(query, remained));
    }

    return result;
  }

  private Set<String> getProjectGroupIds() {
    Set<String> result = new THashSet<String>();
    for (MavenId each : getProjectsIds()) {
      result.add(each.getGroupId());
    }
    return result;
  }

  private Set<String> getProjectArtifactIds(String groupId) {
    Set<String> result = new THashSet<String>();
    for (MavenId each : getProjectsIds()) {
      if (groupId.equals(each.getGroupId())) {
        result.add(each.getArtifactId());
      }
    }
    return result;
  }

  private Set<String> getProjectVersions(String groupId, String artifactId) {
    Set<String> result = new THashSet<String>();
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
    Set<MavenId> result = new THashSet<MavenId>();
    for (MavenProject each : MavenProjectsManager.getInstance(myProject).getProjects()) {
      result.add(each.getMavenId());
    }
    return result;
  }
}
