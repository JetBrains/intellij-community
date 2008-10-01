package org.jetbrains.idea.maven.indices;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.source.ArchetypeDataSource;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.apache.maven.embedder.MavenEmbedder;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.utils.MavenUtil;

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
    ensureInitialized();
    return myIndices;
  }

  private void ensureInitialized() {
    if (myIndices != null) return;

    MavenCoreSettings settings = MavenCore.getInstance(ProjectManager.getInstance().getDefaultProject()).getState();

    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(settings, null).getEmbedder();
    File dir = myTestIndicesDir == null
               ? MavenUtil.getPluginSystemDir("Indices")
               : myTestIndicesDir;
    myIndices = new MavenIndices(myEmbedder, dir, new MavenIndex.IndexListener() {
      public void indexIsBroken(MavenIndex index) {
        scheduleUpdate(Collections.singletonList(index), false);
      }
    });
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
        MavenLog.LOG.error("", e);
      }
      myIndices = null;
    }

    if (myEmbedder != null) {
      try {
        myEmbedder.stop();
      }
      catch (Exception e) {
        MavenLog.LOG.error("", e);
      }
      myEmbedder = null;
    }
  }

  public List<MavenIndex> getIndices() {
    return getIndicesObject().getIndices();
  }

  public synchronized List<MavenIndex> ensureIndicesExist(File localRepository,
                                                          Collection<String> remoteRepositories) {
    // MavenIndices.add method returns an existing index if it has already been added, thus we have to use set here.
    LinkedHashSet<MavenIndex> result = new LinkedHashSet<MavenIndex>();

    MavenIndices indicesObjectCache = getIndicesObject();

    try {
      MavenIndex localIndex = indicesObjectCache.add(localRepository.getPath(), MavenIndex.Kind.LOCAL);
      result.add(localIndex);
      if (localIndex.getUpdateTimestamp() == -1) {
        scheduleUpdate(Collections.singletonList(localIndex));
      }
    }
    catch (MavenIndexException e) {
      MavenLog.LOG.warn(e);
    }

    for (String eachRepo : remoteRepositories) {
      try {
        result.add(indicesObjectCache.add(eachRepo, MavenIndex.Kind.REMOTE));
      }
      catch (MavenIndexException e) {
        MavenLog.LOG.warn(e);
      }
    }

    return new ArrayList<MavenIndex>(result);
  }

  public void addArtifact(File artifactFile, String name) {
    File reporepository = getRepositoryFile(artifactFile, name);

    for (MavenIndex each : getIndices()) {
      if (each.isForLocal(reporepository)) {
        each.addArtifact(artifactFile);
        return;
      }
    }
  }

  private File getRepositoryFile(File artifactFile, String name) {
    List<String> parts = getArtifactParts(name);

    File result = artifactFile;
    for (int i = 0; i < parts.size(); i++) {
      result = result.getParentFile();
    }
    return result;
  }

  private List<String> getArtifactParts(String name) {
    return StringUtil.split(name, "/");
  }


  public void scheduleUpdate(List<MavenIndex> indices) {
    scheduleUpdate(indices, true);
  }

  private void scheduleUpdate(List<MavenIndex> indices, final boolean fullUpdate) {
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
        doUpdateIndices(toSchedule, fullUpdate, indicator);
      }
    });
  }

  private void doUpdateIndices(List<MavenIndex> indices, boolean fullUpdate, ProgressIndicator indicator) {
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
          getIndicesObject().updateOrRepair(each, fullUpdate, indicator);

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              rehighlightAllPoms();
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

  private void rehighlightAllPoms() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Project each : ProjectManager.getInstance().getOpenProjects()) {
          ((PsiModificationTrackerImpl)PsiManager.getInstance(each).getModificationTracker()).incCounter();
          DaemonCodeAnalyzer.getInstance(each).restart();
        }
      }
    });
  }

  public Set<Archetype> getArchetypes() {
    ensureInitialized();
    PlexusContainer container = myEmbedder.getPlexusContainer();
    Set<Archetype> result = new HashSet<Archetype>();
    result.addAll(getArchetypesFrom(container, "internal-catalog"));
    result.addAll(getArchetypesFrom(container, "nexus"));
    return result;
  }

  private List<Archetype> getArchetypesFrom(PlexusContainer container, String roleHint) {
    try {
      ArchetypeDataSource source = (ArchetypeDataSource)container.lookup(ArchetypeDataSource.class, roleHint);
      ArchetypeCatalog catalog = source.getArchetypeCatalog(new Properties());
      return catalog.getArchetypes();
    }
    catch (ComponentLookupException e) {
      MavenLog.LOG.warn(e);
    }
    catch (ArchetypeDataSourceException e) {
      MavenLog.LOG.warn(e);
    }
    return Collections.emptyList();
  }
}
