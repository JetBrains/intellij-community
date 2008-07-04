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
import com.intellij.openapi.ui.Messages;
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
import java.util.ArrayList;
import java.util.List;

public class MavenIndicesManager implements ApplicationComponent {
  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private volatile boolean isInitialized;

  private MavenEmbedder myEmbedder;
  private MavenIndices myIndices;

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

    MavenCoreSettings settings = MavenCore.getInstance(ProjectManager.getInstance().getDefaultProject()).getState();
    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(settings).getEmbedder();
    myIndices = new MavenIndices(myEmbedder, indicesDir);
  }

  public void disposeComponent() {
    doShutdown();
  }

  @TestOnly
  public void doShutdown() {
    if (!isInitialized) return;
    isInitialized = false;

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

  public MavenIndex add(String repositoryPathOrUrl, MavenIndex.Kind kind) throws MavenIndexException {
    return myIndices.add(repositoryPathOrUrl, kind);
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
        try {
          each.addArtifact(artifact);
        }
        catch (MavenIndexException e) {
          MavenLog.error(e);
        }
        return;
      }
    }
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
    catch (final MavenIndexException e) {
      MavenLog.info(e);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(e.getMessage(), RepositoryBundle.message("maven.indices"));
        }
      });
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
}
