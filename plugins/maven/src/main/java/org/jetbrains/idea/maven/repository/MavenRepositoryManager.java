package org.jetbrains.idea.maven.repository;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.apache.lucene.search.Query;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.project.MavenException;
import org.jetbrains.idea.maven.project.MavenImportToolWindow;
import org.jetbrains.idea.maven.state.MavenProjectsManager;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.util.*;

public class MavenRepositoryManager extends DummyProjectComponent {
  private static final String LOCAL_INDICES = "local";
  private static final String PROJECT_INDICIES = "project";

  private MavenEmbedder myEmbedder;
  private MavenRepositoryIndices myIndices;
  private Project myProject;

  public static MavenRepositoryManager getInstance(Project p) {
    return p.getComponent(MavenRepositoryManager.class);
  }

  public MavenRepositoryManager(Project p) {
    myProject = p;
  }

  public void initComponent() {
    StartupManager.getInstance(myProject).registerStartupActivity(new Runnable() {
      public void run() {
        try {
          if (ApplicationManager.getApplication().isUnitTestMode()) return;
          if (!MavenProjectsManager.getInstance(myProject).isMavenProject()) return;

          initIndex();

          StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
            public void run() {
              try {
                ensureHasLocalRepository();
              }
              catch (MavenRepositoryException e) {
                showErrorWhenProjectIsOpen(new MavenException(e));
              }
            }
          });
        }
        catch (MavenException e) {
          showErrorWhenProjectIsOpen(e);
        }
      }
    });
  }

  private void showErrorWhenProjectIsOpen(final MavenException e) {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        MavenLog.LOG.warn(e);
        new MavenImportToolWindow(myProject, "Maven Repository Manager").displayErrors(e);
      }
    });
  }

  @TestOnly
  public void initIndex() throws MavenException {
    myEmbedder = MavenFactory.createEmbedderForExecute(getSettings());
    myIndices = new MavenRepositoryIndices(myEmbedder, getIndicesDir());

    try {
      myIndices.load();
    }
    catch (MavenRepositoryException e) {
      new MavenException("Couldn't load Maven Repositories: " + e.getMessage());
    }
  }

  private MavenCoreSettings getSettings() {
    return MavenCore.getInstance(myProject).getState();
  }

  private File getIndicesDir() {
    File baseDir = new File(PathManager.getSystemPath(), "Maven");
    return new File(baseDir, myProject.getLocationHash());
  }

  public void disposeComponent() {
    closeIndex();
  }

  @TestOnly
  public void closeIndex() {
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

  @TestOnly
  public void clearIndices() {
    FileUtil.delete(getIndicesDir());
  }

  private void ensureHasLocalRepository() throws MavenRepositoryException {
    File localRepoFile = getSettings().getEffectiveLocalRepository();

    MavenRepositoryInfo index = findLocalRepository();
    if (index == null) {
      index = new MavenRepositoryInfo(LOCAL_INDICES, localRepoFile.getPath(), false);
      myIndices.add(index);
      startUpdate(index);
      return;
    }

    if (!index.getRepositoryFile().equals(localRepoFile)) {
      myIndices.change(index, LOCAL_INDICES, localRepoFile.getPath(), false);
      startUpdate(index);
    }
  }

  private MavenRepositoryInfo findLocalRepository() {
    for (MavenRepositoryInfo i : myIndices.getInfos()) {
      if (isLocal(i)) return i;
    }
    return null;
  }

  private boolean isLocal(MavenRepositoryInfo i) {
    return LOCAL_INDICES.equals(i.getId());
  }

  public Configurable createConfigurable() {
    return new MavenRepositoriesConfigurable(myProject, this);
  }

  public void save() {
    myIndices.save();
  }

  public void add(MavenRepositoryInfo i) throws MavenRepositoryException {
    myIndices.add(i);
  }

  public void change(MavenRepositoryInfo i, String id, String repositoryPathOrUrl, boolean isRemote) throws MavenRepositoryException {
    myIndices.change(i, id, repositoryPathOrUrl, isRemote);
  }

  public void remove(MavenRepositoryInfo i) throws MavenRepositoryException {
    myIndices.remove(i);
  }

  public void startUpdate(MavenRepositoryInfo i) {
    doStartUpdate(i);
  }

  public void startUpdateAll() {
    doStartUpdate(null);
  }

  private void doStartUpdate(final MavenRepositoryInfo info) {
    new Task.Backgroundable(myProject, "Updating Maven Repositories...", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<MavenRepositoryInfo> infos = info != null ? Collections.singletonList(info) : myIndices.getInfos();

          for (MavenRepositoryInfo each : infos) {
            indicator.setText("Updating [" + each.getId() + "]");
            myIndices.update(each, indicator);
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              rehighlightAllPoms();
            }
          });
        }
        catch (MavenRepositoryException e) {
          showErrorWhenProjectIsOpen(new MavenException(e));
        }
      }
    }.queue();
  }

  private void rehighlightAllPoms() {
    ((PsiModificationTrackerImpl)PsiManager.getInstance(myProject).getModificationTracker()).incCounter();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  public MavenRepositoryInfo getLocalRepository() {
    return findLocalRepository();
  }

  public List<MavenRepositoryInfo> getUserRepositories() {
    List<MavenRepositoryInfo> result = new ArrayList<MavenRepositoryInfo>();
    for (MavenRepositoryInfo each : myIndices.getInfos()) {
      if (isLocal(each)) continue;
      result.add(each);
    }
    return result;
  }

  public Set<String> getGroupIds() throws MavenRepositoryException {
    return myIndices.getGroupIds();
  }

  public Set<String> getArtifactIds(String groupId) throws MavenRepositoryException {
    return myIndices.getArtifactIds(groupId);
  }

  public Set<String> getVersions(String groupId, String artifactId) throws MavenRepositoryException {
    return myIndices.getVersions(groupId, artifactId);
  }

  public List<ArtifactInfo> findByArtifactId(String pattern) throws MavenRepositoryException {
    return myIndices.findByArtifactId(pattern);
  }

  public List<ArtifactInfo> findByGroupId(String groupId) throws MavenRepositoryException {
    return myIndices.findByGroupId(groupId);
  }

  public Collection<ArtifactInfo> search(Query q) throws MavenRepositoryException {
    return myIndices.search(q);
  }
}
