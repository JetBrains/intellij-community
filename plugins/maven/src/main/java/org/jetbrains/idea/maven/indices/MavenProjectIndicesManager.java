package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class MavenProjectIndicesManager extends DummyProjectComponent {
  private final Project myProject;
  private final AtomicReference<List<MavenIndex>> myProjectIndices = new AtomicReference<List<MavenIndex>>(new ArrayList<MavenIndex>());

  public static MavenProjectIndicesManager getInstance(Project p) {
    return p.getComponent(MavenProjectIndicesManager.class);
  }

  public MavenProjectIndicesManager(Project p) {
    myProject = p;
  }

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myProject.isDefault()) return;

    doInit();
  }

  public void doInit() {
    MavenCore.getInstance(myProject).addConfigurableFactory(new MavenCore.ConfigurableFactory() {
      public Configurable createConfigurable() {
        return new MavenIndicesConfigurable(myProject);
      }
    });

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      updateIndicesList();
    }

    getMavenProjectManager().addListener(new MavenProjectsManager.Listener() {
      public void activate() {
        updateIndicesList();
      }

      public void profilesChanged(List<String> profiles) {
      }

      public void setIgnored(MavenProjectModel project, boolean on) {
      }

      public void projectAdded(MavenProjectModel n) {
        updateIndicesList();
      }

      public void projectUpdated(MavenProjectModel n) {
        updateIndicesList();
      }

      public void projectRemoved(MavenProjectModel n) {
        updateIndicesList();
      }
    });
  }

  private void updateIndicesList() {
    try {
      MavenIndicesManager m = MavenIndicesManager.getInstance();
      myProjectIndices.set(m.ensureIndicesExist(myProject,
                                                MavenProjectsManager.getInstance(myProject).getLocalRepository(),
                                                collectRemoteRepositories()));
    }
    catch (MavenIndexException e) {
      MavenLog.info(e);
    }
  }

  private Set<String> collectRemoteRepositories() {
    Set<String> result = new HashSet<String>();

    for (MavenProjectModel each : getMavenProjectManager().getProjects()) {
      for (ArtifactRepository eachRepository : each.getRepositories()) {
        String url = eachRepository.getUrl();
        if (url == null) continue;
        result.add(url);
      }
    }

    return result;
  }

  public List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myProjectIndices.get());
  }

  public void scheduleUpdateAll() {
    MavenIndicesManager.getInstance().scheduleUpdate(myProject, myProjectIndices.get());
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

  public Set<String> getGroupIds() throws MavenIndexException {
    Set<String> result = getProjectGroupIds();
    for (MavenIndex each : myProjectIndices.get()) {
      result.addAll(each.getGroupIds());
    }
    return result;
  }

  public Set<String> getArtifactIds(String groupId) throws MavenIndexException {
    Set<String> result = getProjectArtifactIds(groupId);
    for (MavenIndex each : myProjectIndices.get()) {
      result.addAll(each.getArtifactIds(groupId));
    }
    return result;
  }

  public Set<String> getVersions(String groupId, String artifactId) throws MavenIndexException {
    Set<String> result = getProjectVersions(groupId, artifactId);
    for (MavenIndex each : myProjectIndices.get()) {
      result.addAll(each.getVersions(groupId, artifactId));
    }
    return result;
  }

  public boolean hasGroupId(String groupId) throws MavenIndexException {
    if (hasProjectGroupId(groupId)) return true;
    for (MavenIndex each : myProjectIndices.get()) {
      if (each.hasGroupId(groupId)) return true;
    }
    return false;
  }

  public boolean hasArtifactId(String groupId, String artifactId) throws MavenIndexException {
    if (hasProjectArtifactId(groupId, artifactId)) return true;
    for (MavenIndex each : myProjectIndices.get()) {
      if (each.hasArtifactId(groupId, artifactId)) return true;
    }
    return false;
  }

  public boolean hasVersion(String groupId, String artifactId, String version) throws MavenIndexException {
    if (hasProjectVersion(groupId, artifactId, version)) return true;
    for (MavenIndex each : myProjectIndices.get()) {
      if (each.hasVersion(groupId, artifactId, version)) return true;
    }
    return false;
  }

  private Set<String> getProjectGroupIds() {
    Set<String> result = new HashSet<String>();
    for (MavenId each : getProjectsIds()) {
      result.add(each.groupId);
    }
    return result;
  }

  private Set<String> getProjectArtifactIds(String groupId) {
    Set<String> result = new HashSet<String>();
    for (MavenId each : getProjectsIds()) {
      if (groupId.equals(each.groupId)) {
        result.add(each.artifactId);
      }
    }
    return result;
  }

  private Set<String> getProjectVersions(String groupId, String artifactId) {
    Set<String> result = new HashSet<String>();
    for (MavenId each : getProjectsIds()) {
      if (groupId.equals(each.groupId) && artifactId.equals(each.artifactId)) {
        result.add(each.version);
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
    Set<MavenId> result = new HashSet<MavenId>();
    for (MavenProjectModel each : MavenProjectsManager.getInstance(myProject).getProjects()) {
      result.add(each.getMavenId());
    }
    return result;
  }
}
