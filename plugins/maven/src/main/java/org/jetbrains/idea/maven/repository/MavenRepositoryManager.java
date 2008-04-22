package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.project.MavenException;
import org.jetbrains.idea.maven.state.MavenProjectsManager;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.util.List;

public class MavenRepositoryManager extends DummyProjectComponent {
  private MavenEmbedder myEmbedder;
  private MavenRepositoryIndex myIndex;
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
          if (!MavenProjectsManager.getInstance(myProject).isMavenProject()) return;
          if (ApplicationManager.getApplication().isUnitTestMode()) return;
          
          File baseDir = new File(PathManager.getSystemPath(), "Maven");
          File projectIndecesDir = new File(baseDir, myProject.getLocationHash());

          myEmbedder = MavenFactory.createEmbedderForExecute(MavenCore.getInstance(myProject).getState());
          myIndex = new MavenRepositoryIndex(myEmbedder, projectIndecesDir);

          StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
            public void run() {
              try {
                addAndUpdateLocalRepository();
              }
              catch (MavenRepositoryException e) {
                throw new RuntimeException(e);
              }
            }
          });
        }
        catch (MavenException e) {
          throw new RuntimeException(e);
        }
        catch (MavenRepositoryException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private void addAndUpdateLocalRepository() throws MavenRepositoryException {
    String repoDir = myEmbedder.getLocalRepository().getBasedir();
    List<MavenRepositoryInfo> infos = myIndex.getInfos();

    for (MavenRepositoryInfo i : infos) {
      if (i.getId().equals("local")) return;
    }

    MavenRepositoryInfo i = new MavenRepositoryInfo("local", repoDir, false);
    myIndex.add(i);
    startUpdate(i);
  }

  public void disposeComponent() {
    try {
      if (myIndex != null) myIndex.close();
      if (myEmbedder != null) myEmbedder.stop();
    }
    catch (MavenEmbedderException e) {
      throw new RuntimeException(e);
    }
  }

  public Configurable createConfigurable() {
    return new MavenRepositoriesConfigurable(myProject, this);
  }

  public void save() {
    myIndex.save();
  }

  public void add(MavenRepositoryInfo i) throws MavenRepositoryException {
    myIndex.add(i);
  }

  public void change(MavenRepositoryInfo i, String id, String repositoryPathOrUrl, boolean isRemote) throws MavenRepositoryException {
    myIndex.change(i, id, repositoryPathOrUrl, isRemote);
  }

  public void remove(MavenRepositoryInfo i) throws MavenRepositoryException {
    myIndex.remove(i);
  }

  public void startUpdate(MavenRepositoryInfo i) {
    doStartUpdate(i);
  }

  public void startUpdateAll() {
    doStartUpdate(null);
  }

  private void doStartUpdate(final MavenRepositoryInfo i) {
    new Task.Backgroundable(myProject, "Updating Maven Repositories...", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          if (i != null) {
            myIndex.update(i, indicator);
          }
          else {
            for (MavenRepositoryInfo each : myIndex.getInfos()) {
              myIndex.update(each, indicator);
            }
          }
        }
        catch (final MavenRepositoryException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(e.getMessage(), "Maven Repository Index");
            }
          });
        }
      }
    }.queue();
  }

  public List<MavenRepositoryInfo> getInfos() {
    return myIndex.getInfos();
  }

  public List<ArtifactInfo> find(String pattern) throws MavenRepositoryException {
    return myIndex.find(pattern);
  }
}
