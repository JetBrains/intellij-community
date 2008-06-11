package org.jetbrains.idea.maven.repository;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.util.*;

public class MavenIndicesManager implements ApplicationComponent {
  private boolean isInitialized;

  private MavenEmbedder myEmbedder;
  private MavenIndices myIndices;

  private Map<Project, MavenIndex> myMavenProjectIndices = new HashMap<Project, MavenIndex>();
  private Map<Project, MavenProjectsManager.Listener> myMavenProjectListeners = new HashMap<Project, MavenProjectsManager.Listener>();

  public static MavenIndicesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(MavenIndicesManager.class);
  }

  @NotNull
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    doInit(new File(PathManager.getSystemPath(), "Maven/Indices"));
  }

  @TestOnly
  public void doInit(File indicesDir) {
    isInitialized = true;

    initIndices(indicesDir);
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(Project project) {
        initProjectIndices(project);
      }

      @Override
      public void projectClosed(Project project) {
        shutdownProjectIndices(project);
      }
    });
  }

  private void initIndices(File indicesDir) {
    MavenCoreSettings settings = getSettings(ProjectManager.getInstance().getDefaultProject());
    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(settings);
    myIndices = new MavenIndices(myEmbedder, indicesDir);

    myIndices.load();
  }

  @TestOnly
  public void initProjectIndices(Project p) {
    try {
      checkLocalIndex(p);
      checkProjectIndex(p);
    }
    catch (MavenIndexException e) {
      showError(e);
    }

    listenForChanges(p);
  }

  private void listenForChanges(final Project p) {
    MavenProjectsManager.Listener l = new MavenProjectsManager.Listener() {
      public void activate() {
      }

      public void profilesChanged(List<String> profiles) {
      }

      public void setIgnored(VirtualFile file, boolean on) {
      }

      public void projectAdded(MavenProjectModel n) {
        addArtifact(myMavenProjectIndices.get(p), n.getMavenId());
      }

      public void projectRemoved(MavenProjectModel n) {
        myMavenProjectIndices.get(p).removeArtifact(n.getMavenId());
      }

      public void beforeProjectUpdate(MavenProjectModel n) {
        projectRemoved(n);
      }

      public void projectUpdated(MavenProjectModel n) {
        projectAdded(n);
      }
    };
    MavenProjectsManager.getInstance(p).addListener(l);
    myMavenProjectListeners.put(p, l);
  }

  private void shutdownProjectIndices(Project p) {
    MavenProjectsManager.Listener l = myMavenProjectListeners.remove(p);
    MavenProjectsManager.getInstance(p).removeListener(l);
    myMavenProjectIndices.remove(p);
  }

  private void showError(MavenIndexException e) {
    MavenLog.warn(e);
    //new MavenErrorWindow(p, RepositoryBundle.message("maven.indices")).displayErrors(e);
  }

  private MavenCoreSettings getSettings(Project p) {
    return MavenCore.getInstance(p).getState();
  }

  public void disposeComponent() {
    doShutdown();
  }

  public void doShutdown() {
    if (!isInitialized) return;
    closeIndex();
    isInitialized = false;
  }

  private void closeIndex() {
    try {
      if (myIndices != null) {
        myIndices.save();
        myIndices.close();
      }
      if (myEmbedder != null) myEmbedder.stop();
      myIndices = null;
      myEmbedder = null;
    }
    catch (MavenEmbedderException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkLocalIndex(Project p) throws MavenIndexException {
    File localRepoFile = getSettings(p).getEffectiveLocalRepository();

    for (MavenIndex i : myIndices.getIndices()) {
      if (localRepoFile.equals(i.getRepositoryFile())) return;
    }

    String repoPath = localRepoFile.getPath();
    MavenIndex index = new LocalMavenIndex("local (" + Integer.toHexString(repoPath.hashCode()) + ")", repoPath);
    myIndices.add(index);
    scheduleUpdate(p, index);
  }

  private void checkProjectIndex(Project p) throws MavenIndexException {
    MavenIndex projectIndex = null;

    String indexId = p.getLocationHash();

    for (MavenIndex i : myIndices.getIndices()) {
      if (indexId.equals(i.getId())) {
        projectIndex = i;
        break;
      }
    }

    if (projectIndex == null) {
      projectIndex = new ProjectMavenIndex(indexId, p.getBaseDir().getPath());
      myIndices.add(projectIndex);
    }

    myMavenProjectIndices.put(p, projectIndex);
    scheduleUpdate(p, projectIndex);
  }

  public Configurable createConfigurable(Project p) {
    return new MavenIndicesConfigurable(p, this);
  }

  public void save() {
    myIndices.save();
  }

  public void add(MavenIndex i) throws MavenIndexException {
    myIndices.add(i);
  }

  public void change(MavenIndex i, String id, String repositoryPathOrUrl) throws MavenIndexException {
    myIndices.change(i, id, repositoryPathOrUrl);
  }

  public void remove(MavenIndex i) throws MavenIndexException {
    myIndices.remove(i);
  }

  public void addArtifact(File repository, MavenId artifact) {
    if (!isInitialized) return;
    for (MavenIndex each : getIndices()) {
      if (repository.equals(each.getRepositoryFile())) {
        addArtifact(each, artifact);
        return;
      }
    }
  }

  private void addArtifact(MavenIndex each, MavenId artifact) {
    try {
      each.addArtifact(artifact);
    }
    catch (MavenIndexException e) {
      showError(e);
    }
  }

  public void scheduleUpdate(Project p, MavenIndex i) {
    doScheduleUpdate(p, i);
  }

  public void scheduleUpdateAll(Project p) {
    doScheduleUpdate(p, null);
  }

  private void doScheduleUpdate(final Project p, final MavenIndex index) {
    new Task.Backgroundable(null, RepositoryBundle.message("maven.indices.updating"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<MavenIndex> infos = index != null
                                   ? Collections.singletonList(index)
                                   : myIndices.getIndices();

          try {
            for (MavenIndex each : infos) {
              indicator.setText(RepositoryBundle.message("maven.indices.updating.index", each.getId()));
              myIndices.update(each, p, indicator);
            }
          }
          catch (ProcessCanceledException ignore) {
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              rehighlightAllPoms(p);
            }
          });
        }
        catch (MavenIndexException e) {
          showError(e);
        }
      }
    }.queue();
  }

  private void rehighlightAllPoms(final Project p) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ((PsiModificationTrackerImpl)PsiManager.getInstance(p).getModificationTracker()).incCounter();
        DaemonCodeAnalyzer.getInstance(p).restart();
      }
    });
  }

  public List<MavenIndex> getIndices() {
    return myIndices.getIndices();
  }

  public Set<String> getGroupIds() throws MavenIndexException {
    return myIndices.getGroupIds();
  }

  public boolean hasGroupId(String groupId) throws MavenIndexException {
    return myIndices.hasGroupId(groupId);
  }

  public Set<String> getArtifactIds(String groupId) throws MavenIndexException {
    return myIndices.getArtifactIds(groupId);
  }

  public boolean hasArtifactId(String groupId, String artifactId) throws MavenIndexException {
    return myIndices.hasArtifactId(groupId, artifactId);
  }

  public Set<String> getVersions(String groupId, String artifactId) throws MavenIndexException {
    return myIndices.getVersions(groupId, artifactId);
  }

  public boolean hasVersion(String groupId, String artifactId, String version) throws MavenIndexException {
    return myIndices.hasVersion(groupId, artifactId, version);
  }
}
