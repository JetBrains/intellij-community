package org.jetbrains.idea.maven.repository;

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

  private Map<Project, MavenIndex> myMavenProjectIndices = new HashMap<Project, MavenIndex>();
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

    myIndices.load();
  }

  @TestOnly
  public void initProjectIndicesOnActivation(final Project p) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doInitProjectIndices(p);
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
        if (!myMavenProjectIndices.containsKey(p)) return;
        addArtifact(myMavenProjectIndices.get(p), n.getMavenId());
      }

      public void projectRemoved(MavenProjectModel n) {
        if (!myMavenProjectIndices.containsKey(p)) return;
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

  private void doInitProjectIndices(Project p) {
    try {
      checkLocalIndex(p);
      checkProjectIndex(p);
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
        myIndices.save();
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

    MavenIndex index = new LocalMavenIndex(f.getPath());
    myIndices.add(index);
    scheduleUpdate(p, index);
  }

  private void checkProjectIndex(Project p) throws MavenIndexException {
    MavenIndex projectIndex = null;

    for (MavenIndex i : myIndices.getIndices()) {
      if (p.getBaseDir().getPath().equals(i.getRepositoryPathOrUrl())) {
        projectIndex = i;
        break;
      }
    }

    if (projectIndex == null) {
      projectIndex = new ProjectMavenIndex(p.getBaseDir().getPath());
      myIndices.add(projectIndex);
    }

    myMavenProjectIndices.put(p, projectIndex);
    scheduleUpdate(p, projectIndex);
  }

  public Configurable createConfigurable(Project p) {
    return new MavenIndicesConfigurable(p, this);
  }

  public void save() throws MavenIndexException {
    myIndices.save();
  }

  public void add(MavenIndex i) throws MavenIndexException {
    myIndices.add(i);
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
            myIndices.update(each, p, indicator);
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
