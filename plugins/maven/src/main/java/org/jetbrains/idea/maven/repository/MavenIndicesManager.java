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

public class MavenIndicesManager extends DummyProjectComponent {
  private static final String LOCAL_INDEX = "local";
  private static final String PROJECT_INDEX = "project";

  private MavenEmbedder myEmbedder;
  private MavenIndices myIndices;
  private Project myProject;

  public static MavenIndicesManager getInstance(Project p) {
    return p.getComponent(MavenIndicesManager.class);
  }

  public MavenIndicesManager(Project p) {
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
                checkLocalIndex();
                checkProjectIndex();
              }
              catch (MavenIndexException e) {
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
        new MavenImportToolWindow(myProject, RepositoryBundle.message("maven.indices")).displayErrors(e);
      }
    });
  }

  @TestOnly
  public void initIndex() throws MavenException {
    myEmbedder = MavenFactory.createEmbedderForExecute(getSettings());
    myIndices = new MavenIndices(myEmbedder, getIndicesDir());

    try {
      myIndices.load();
    }
    catch (MavenIndexException e) {
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

  private void checkLocalIndex() throws MavenIndexException {
    File localRepoFile = getSettings().getEffectiveLocalRepository();

    MavenIndex index = findLocalIndex();
    if (index == null) {
      index = new MavenIndex(LOCAL_INDEX, localRepoFile.getPath(), MavenIndex.Kind.LOCAL);
      myIndices.add(index);
      startUpdate(index);
      return;
    }

    if (!index.getRepositoryFile().equals(localRepoFile)) {
      myIndices.change(index, LOCAL_INDEX, localRepoFile.getPath());
      startUpdate(index);
    }
  }

  private void checkProjectIndex() throws MavenIndexException {
    //MavenIndex i = findProjectIndex();
    //if (i == null) {
    //  i = new MavenIndex(PROJECT_INDEX, null, MavenIndex.Kind.PROJECT);
    //  myIndices.add(i);
    //  startUpdate(i);
    //}
  }

  private MavenIndex findLocalIndex() {
    for (MavenIndex i : myIndices.getIndices()) {
      if (isLocal(i)) return i;
    }
    return null;
  }

  private MavenIndex findProjectIndex() {
    for (MavenIndex i : myIndices.getIndices()) {
      if (isLocal(i)) return i;
    }
    return null;
  }

  private boolean isLocal(MavenIndex i) {
    return LOCAL_INDEX.equals(i.getId());
  }

  public Configurable createConfigurable() {
    return new MavenIndicesConfigurable(myProject, this);
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

  public void startUpdate(MavenIndex i) {
    doStartUpdate(i);
  }

  public void startUpdateAll() {
    doStartUpdate(null);
  }

  private void doStartUpdate(final MavenIndex info) {
    new Task.Backgroundable(myProject, RepositoryBundle.message("maven.indices.updating"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<MavenIndex> infos = info != null ? Collections.singletonList(info) : myIndices.getIndices();

          for (MavenIndex each : infos) {
            indicator.setText(RepositoryBundle.message("maven.indices.updating.index", each.getId()));
            myIndices.update(each, indicator);
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              rehighlightAllPoms();
            }
          });
        }
        catch (MavenIndexException e) {
          showErrorWhenProjectIsOpen(new MavenException(e));
        }
      }
    }.queue();
  }

  private void rehighlightAllPoms() {
    ((PsiModificationTrackerImpl)PsiManager.getInstance(myProject).getModificationTracker()).incCounter();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  public MavenIndex getLocalIndex() {
    return findLocalIndex();
  }

  public List<MavenIndex> getUserIndices() {
    List<MavenIndex> result = new ArrayList<MavenIndex>();
    for (MavenIndex each : myIndices.getIndices()) {
      if (isLocal(each)) continue;
      result.add(each);
    }
    return result;
  }

  public Set<String> getGroupIds() throws MavenIndexException {
    return myIndices.getGroupIds();
  }

  public Set<String> getArtifactIds(String groupId) throws MavenIndexException {
    return myIndices.getArtifactIds(groupId);
  }

  public Set<String> getVersions(String groupId, String artifactId) throws MavenIndexException {
    return myIndices.getVersions(groupId, artifactId);
  }

  public List<ArtifactInfo> findByArtifactId(String pattern) throws MavenIndexException {
    return myIndices.findByArtifactId(pattern);
  }

  public List<ArtifactInfo> findByGroupId(String groupId) throws MavenIndexException {
    return myIndices.findByGroupId(groupId);
  }

  public Collection<ArtifactInfo> search(Query q) throws MavenIndexException {
    return myIndices.search(q);
  }
}
