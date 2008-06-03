package org.jetbrains.idea.maven.repository;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.apache.lucene.search.Query;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenConstants;
import org.jetbrains.idea.maven.project.MavenException;
import org.jetbrains.idea.maven.project.MavenImportToolWindow;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.util.*;

public class MavenIndicesManager extends DummyProjectComponent {
  private static final String LOCAL_INDEX = "local";
  private static final String PROJECT_INDEX = "project";

  private boolean isInitialized;

  private MavenEmbedder myEmbedder;
  private MavenIndices myIndices;
  private Project myProject;
  private VirtualFileAdapter myFileListener;

  public static MavenIndicesManager getInstance(Project p) {
    return p.getComponent(MavenIndicesManager.class);
  }

  public MavenIndicesManager(Project p) {
    myProject = p;
  }

  public void doInit() {
    isInitialized = true;

    try {
      initIndices();

      try {
        checkLocalIndex();
        checkProjectIndex();
      }
      catch (MavenIndexException e) {
        throw new MavenException(e);
      }
    }
    catch (MavenException e) {
      showError(e);
    }

    listenForArtifactChanges();
  }

  private void initIndices() {
    myEmbedder = MavenEmbedderFactory.createEmbedderForExecute(getSettings());
    myIndices = new MavenIndices(myEmbedder, getIndicesDir());

    myIndices.load();
  }

  private void listenForArtifactChanges() {
    myFileListener = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent event) {
        doUpdate(event);
      }

      @Override
      public void beforeContentsChange(VirtualFileEvent event) {
        doUpdate(event);
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        doUpdate(event);
      }
      
      private void doUpdate(final VirtualFileEvent event) {
        if (!event.getFileName().equals(MavenConstants.POM_XML)) return;
        startUpdate(findProjectIndex());
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);
  }


  private void showError(final MavenException e) {
    MavenLog.LOG.warn(e);
    new MavenImportToolWindow(myProject, RepositoryBundle.message("maven.indices")).displayErrors(e);
  }

  private MavenCoreSettings getSettings() {
    return MavenCore.getInstance(myProject).getState();
  }

  private File getIndicesDir() {
    File baseDir = new File(PathManager.getSystemPath(), "Maven");
    return new File(baseDir, myProject.getLocationHash());
  }

  public void disposeComponent() {
    doShutdown();
  }

  public void doShutdown() {
    if (!isInitialized) return;

    VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
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
    MavenIndex i = findProjectIndex();
    if (i == null) {
      i = new MavenIndex(PROJECT_INDEX, myProject.getBaseDir().getPath(), MavenIndex.Kind.PROJECT);
      myIndices.add(i);
    } else {
      myIndices.change(i, PROJECT_INDEX, myProject.getBaseDir().getPath());
    }
    startUpdate(i);
  }

  private MavenIndex findLocalIndex() {
    for (MavenIndex i : myIndices.getIndices()) {
      if (i.getKind() == MavenIndex.Kind.LOCAL) return i;
    }
    return null;
  }

  private MavenIndex findProjectIndex() {
    for (MavenIndex i : myIndices.getIndices()) {
      if (i.getKind() == MavenIndex.Kind.PROJECT) return i;
    }
    return null;
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

          try {
            for (MavenIndex each : infos) {
              indicator.setText(RepositoryBundle.message("maven.indices.updating.index", each.getId()));
              myIndices.update(each, myProject, indicator);
            }
          }
          catch (ProcessCanceledException ignore) {
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              rehighlightAllPoms();
            }
          });
        }
        catch (MavenIndexException e) {
          showError(new MavenException(e));
        }
      }
    }.queue();
  }

  private void rehighlightAllPoms() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ((PsiModificationTrackerImpl)PsiManager.getInstance(myProject).getModificationTracker()).incCounter();
        DaemonCodeAnalyzer.getInstance(myProject).restart();
      }
    });
  }

  public MavenIndex getLocalIndex() {
    return findLocalIndex();
  }

  public MavenIndex getProjectIndex() {
    return findProjectIndex();
  }

  public List<MavenIndex> getUserIndices() {
    List<MavenIndex> result = new ArrayList<MavenIndex>();
    for (MavenIndex each : myIndices.getIndices()) {
      if (each.getKind() == MavenIndex.Kind.REMOTE)  {
        result.add(each);
      }
    }
    return result;
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
