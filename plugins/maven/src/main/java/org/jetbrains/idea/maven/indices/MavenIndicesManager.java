package org.jetbrains.idea.maven.indices;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.apache.maven.embedder.MavenEmbedder;
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
  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private boolean isInitialized;

  private MavenEmbedder myEmbedder;
  private MavenIndices myIndices;

  private Map<Project, ProjectMavenIndex> myMavenProjectIndices = new HashMap<Project, ProjectMavenIndex>();
  private Map<Project, MavenProjectsManager.Listener> myMavenProjectListeners = new HashMap<Project, MavenProjectsManager.Listener>();

  private final Object myUpdatingIndicesLock = new Object();
  private List<MavenIndex> myWaitingIndices = new ArrayList<MavenIndex>();
  private MavenIndex myUpdatingIndex;

  private BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(RepositoryBundle.message("maven.indices.updating"));

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
        initProjectIndicesOnActivation(project);
      }

      @Override
      public void projectClosed(Project project) {
        shutdownProjectIndices(project);
      }
    });
  }

  private void initIndices(File indicesDir) {
    MavenCoreSettings settings = getSettings(ProjectManager.getInstance().getDefaultProject());
    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(settings).getEmbedder();
    myIndices = new MavenIndices(myEmbedder, indicesDir);
  }

  @TestOnly
  public void initProjectIndicesOnActivation(final Project p) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doInitProjectIndices(p);
      return;
    }

    MavenProjectsManager.Listener l = new MavenProjectsManager.Listener() {
      public void activate() {
        doInitProjectIndices(p);
      }

      public void profilesChanged(List<String> profiles) {
      }

      public void setIgnored(VirtualFile file, boolean on) {
      }

      public void projectAdded(MavenProjectModel n) {
      }

      public void projectRemoved(MavenProjectModel n) {
      }

      public void beforeProjectUpdate(MavenProjectModel n) {
      }

      public void projectUpdated(MavenProjectModel n) {
      }
    };
    MavenProjectsManager.getInstance(p).addListener(l);
    myMavenProjectListeners.put(p, l);
  }

  private void doInitProjectIndices(Project p) {
    try {
      checkLocalIndex(p);
      createProjectIndex(p);
    }
    catch (MavenIndexException e) {
      showError(e);
    }
  }

  private void shutdownProjectIndices(Project p) {
    MavenProjectsManager.Listener l = myMavenProjectListeners.remove(p);
    if (l == null) return; // was not initialized

    MavenProjectsManager.getInstance(p).removeListener(l);
    myMavenProjectIndices.remove(p);
  }

  private void showError(final MavenIndexException e) {
    MavenLog.info(e);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(e.getMessage(), RepositoryBundle.message("maven.indices"));
      }
    });
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
        myIndices.close();
      }
      if (myEmbedder != null) myEmbedder.stop();
      myIndices = null;
      myEmbedder = null;
    }
    catch (Exception e) {
      MavenLog.error(e);
    }
  }

  private void checkLocalIndex(Project p) throws MavenIndexException {
    File localRepoFile = getSettings(p).getEffectiveLocalRepository();
    if (localRepoFile == null) return;

    VirtualFile f = LocalFileSystem.getInstance().findFileByIoFile(localRepoFile);
    if (f == null) return;

    for (MavenIndex i : myIndices.getIndices()) {
      if (f.getPath().equals(i.getRepositoryPathOrUrl())) return;
    }

    MavenIndex index = myIndices.add(f.getPath(), MavenIndex.Kind.LOCAL);
    scheduleUpdate(p, index);
  }

  private void createProjectIndex(Project p) throws MavenIndexException {
    myMavenProjectIndices.put(p, new ProjectMavenIndex(p));
  }

  public Configurable createConfigurable(Project p) {
    return new MavenIndicesConfigurable(p, this);
  }

  public void add(String repositoryPathOrUrl, MavenIndex.Kind kind) throws MavenIndexException {
    myIndices.add(repositoryPathOrUrl, kind);
  }

  public void change(MavenIndex i, String repositoryPathOrUrl) throws MavenIndexException {
    myIndices.change(i, repositoryPathOrUrl);
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
      MavenLog.error(e);
    }
  }

  public void scheduleUpdate(Project p, MavenIndex i) {
    doScheduleUpdate(p, i);
  }

  public void scheduleUpdateAll(Project p) {
    doScheduleUpdate(p, null);
  }

  private void doScheduleUpdate(final Project p, final MavenIndex index) {
    List<MavenIndex> all = index != null
                           ? Collections.singletonList(index)
                           : myIndices.getIndices();
    final List<MavenIndex> toSchedule = new ArrayList<MavenIndex>();

    synchronized (myUpdatingIndicesLock) {
      for (MavenIndex each : all) {
        if (myWaitingIndices.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndices.addAll(toSchedule);
    }

    myUpdatingQueue.run(new Task.Backgroundable(null, RepositoryBundle.message("maven.indices.updating"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        doUpdateIndices(toSchedule, p, indicator);
      }
    });
  }

  private void doUpdateIndices(List<MavenIndex> indices, final Project p, ProgressIndicator indicator) {
    try {
      List<MavenIndex> remainingWaiting = new ArrayList<MavenIndex>(indices);

      try {
        for (MavenIndex each : indices) {
          if (indicator.isCanceled()) return;

          indicator.setText(RepositoryBundle.message("maven.indices.updating.index", each.getRepositoryPathOrUrl()));

          synchronized (myUpdatingIndicesLock) {
            remainingWaiting.remove(each);
            myWaitingIndices.remove(each);
            myUpdatingIndex = each;
          }

          try {
            myIndices.update(each, indicator);

            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                rehighlightAllPoms(p);
              }
            });
          }
          finally {
            synchronized (myUpdatingIndicesLock) {
              myUpdatingIndex = null;
            }
          }
        }
      }
      catch (ProcessCanceledException ignore) {
      }
      finally {
        synchronized (myUpdatingIndicesLock) {
          myWaitingIndices.removeAll(remainingWaiting);
        }
      }
    }
    catch (MavenIndexException e) {
      showError(e);
    }
  }

  public IndexUpdatingState getUpdatingState(MavenIndex index) {
    synchronized (myUpdatingIndicesLock) {
      if (myUpdatingIndex == index) return IndexUpdatingState.UPDATING;
      if (myWaitingIndices.contains(index)) return IndexUpdatingState.WAITING;
      return IndexUpdatingState.IDLE;
    }
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

  public Set<String> getGroupIds(Project p) throws MavenIndexException {
    Set<String> result = myIndices.getGroupIds();
    result.addAll(getProjectIndex(p).getGroupIds());
    return result;
  }

  public Set<String> getArtifactIds(Project p, String groupId) throws MavenIndexException {
    Set<String> result = myIndices.getArtifactIds(groupId);
    result.addAll(getProjectIndex(p).getArtifactIds(groupId));
    return result;
  }

  public Set<String> getVersions(Project p, String groupId, String artifactId) throws MavenIndexException {
    Set<String> result = myIndices.getVersions(groupId, artifactId);
    result.addAll(getProjectIndex(p).getVersions(groupId, artifactId));
    return result;
  }

  public boolean hasGroupId(Project p, String groupId) throws MavenIndexException {
    return getProjectIndex(p).hasGroupId(groupId)
           || myIndices.hasGroupId(groupId);
  }

  public boolean hasArtifactId(Project p, String groupId, String artifactId) throws MavenIndexException {
    return getProjectIndex(p).hasArtifactId(groupId, artifactId)
           || myIndices.hasArtifactId(groupId, artifactId);
  }

  public boolean hasVersion(Project p, String groupId, String artifactId, String version) throws MavenIndexException {
    return getProjectIndex(p).hasVersion(groupId, artifactId, version)
           || myIndices.hasVersion(groupId, artifactId, version);
  }

  private ProjectMavenIndex getProjectIndex(Project p) {
    return myMavenProjectIndices.get(p);
  }
}
