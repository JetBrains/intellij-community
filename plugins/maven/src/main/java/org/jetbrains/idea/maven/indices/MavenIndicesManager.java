package org.jetbrains.idea.maven.indices;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
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

import java.io.File;
import java.util.*;

public class MavenIndicesManager implements ApplicationComponent {
  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private File myTestIndicesDir;

  private volatile MavenEmbedder myEmbedder;
  private volatile MavenIndices myIndices;

  private final Object myUpdatingIndicesLock = new Object();
  private final List<MavenIndex> myWaitingIndices = new ArrayList<MavenIndex>();
  private MavenIndex myUpdatingIndex;

  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(IndicesBundle.message("maven.indices.updating"));

  public static MavenIndicesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(MavenIndicesManager.class);
  }

  @NotNull
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  public void initComponent() {
  }

  @TestOnly
  public void setTestIndexDir(File indicesDir) {
    myTestIndicesDir = indicesDir;
  }

  private synchronized MavenIndices getIndicesObject() {
    if (myIndices != null) return myIndices;

    MavenCoreSettings settings = MavenCore.getInstance(ProjectManager.getInstance().getDefaultProject()).getState();

    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(settings).getEmbedder();
    File dir = myTestIndicesDir == null
               ? new File(PathManager.getSystemPath(), "Maven/Indices")
               : myTestIndicesDir;
    myIndices = new MavenIndices(myEmbedder, dir);

    return myIndices;
  }

  public void disposeComponent() {
    doShutdown();
  }

  @TestOnly
  public void doShutdown() {
    if (myIndices != null) {
      try {
        myIndices.close();
      }
      catch (Exception e) {
        MavenLog.error(e);
      }
      myIndices = null;
    }

    if (myEmbedder != null) {
      try {
        myEmbedder.stop();
      }
      catch (Exception e) {
        MavenLog.error(e);
      }
      myEmbedder = null;
    }
  }

  public List<MavenIndex> getIndices() {
    return getIndicesObject().getIndices();
  }

  public synchronized List<MavenIndex> ensureIndicesExist(Project project,
                                                          File localRepository,
                                                          Collection<String> remoteRepositories) throws MavenIndexException {
    // MavenIndices.add method returns an existing index if it has already been added, thus we have to use set here.
    LinkedHashSet<MavenIndex> result = new LinkedHashSet<MavenIndex>();

    MavenIndices indicesObjectCache = getIndicesObject();

    MavenIndex localIndex = indicesObjectCache.add(localRepository.getPath(), MavenIndex.Kind.LOCAL);
    result.add(localIndex);
    if (localIndex.getUpdateTimestamp() == -1) {
      scheduleUpdate(project, Collections.singletonList(localIndex));
    }

    for (String eachRepo : remoteRepositories) {
      result.add(indicesObjectCache.add(eachRepo, MavenIndex.Kind.REMOTE));
    }

    return new ArrayList<MavenIndex>(result);
  }

  public void addArtifact(File repository, MavenId artifact) {
    for (MavenIndex each : getIndices()) {
      if (repository.equals(each.getRepositoryFile())) {
        try {
          each.addArtifact(artifact);
        }
        catch (MavenIndexException e) {
          MavenLog.error(logIndexError(each), e);
        }
        return;
      }
    }
  }

  private String logIndexError(MavenIndex each) {
    String message = "Failed with fix #1 for index " + each.getDir();
    String separator = "\n-------------------------------------------------";
    for (MavenIndex eachExistingIndex : getIndices()) {
      message += separator +
                 "\ndir: " + eachExistingIndex.getDir() +
                 "\ndataDir: " + eachExistingIndex.getCurrentDataDir() +
                 "\nrepository: " + eachExistingIndex.getRepositoryPathOrUrl();
    }
    message += separator;
    return message;
  }

  public void scheduleUpdate(final Project p, List<MavenIndex> indices) {
    final List<MavenIndex> toSchedule = new ArrayList<MavenIndex>();

    synchronized (myUpdatingIndicesLock) {
      for (MavenIndex each : indices) {
        if (myWaitingIndices.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndices.addAll(toSchedule);
    }

    myUpdatingQueue.run(new Task.Backgroundable(null, IndicesBundle.message("maven.indices.updating"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        doUpdateIndices(toSchedule, p, indicator);
      }
    });
  }

  private void doUpdateIndices(List<MavenIndex> indices, final Project p, ProgressIndicator indicator) {
    List<MavenIndex> remainingWaiting = new ArrayList<MavenIndex>(indices);

    try {
      for (MavenIndex each : indices) {
        if (indicator.isCanceled()) return;

        indicator.setText(IndicesBundle.message("maven.indices.updating.index", each.getRepositoryPathOrUrl()));

        synchronized (myUpdatingIndicesLock) {
          remainingWaiting.remove(each);
          myWaitingIndices.remove(each);
          myUpdatingIndex = each;
        }

        try {
          getIndicesObject().update(each, indicator);

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
}
