package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Plugin;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.io.File;
import java.util.*;

public class MavenArtifactDownloader {
  private final MavenArtifactSettings mySettings;
  private final MavenEmbedderWrapper myEmbedder;
  private final MavenProcess myProgress;
  private final MavenProjectsTree myProjectsTree;
  private final List<MavenProjectModel> myMavenProjects;

  public static void download(MavenProjectsTree projectsTree,
                              List<MavenProjectModel> mavenProjects,
                              MavenArtifactSettings settings,
                              boolean demand,
                              MavenEmbedderWrapper embedder,
                              MavenProcess p) throws MavenProcessCanceledException {
    new MavenArtifactDownloader(projectsTree, mavenProjects, settings, embedder, p).download(demand);
  }

  private MavenArtifactDownloader(MavenProjectsTree projectsTree,
                                  List<MavenProjectModel> mavenProjects,
                                  MavenArtifactSettings settings,
                                  MavenEmbedderWrapper embedder,
                                  MavenProcess p) {
    myProjectsTree = projectsTree;
    myMavenProjects = mavenProjects;
    mySettings = settings;
    myEmbedder = embedder;
    myProgress = p;
  }

  private void download(boolean demand) throws MavenProcessCanceledException {
    List<File> downloadedFiles = new ArrayList<File>();
    try {
      Map<Artifact, Set<ArtifactRepository>> artifacts = collectArtifactsToDownload();

      if (shouldDownload(mySettings.getDownloadSources(), demand)) {
        download(MavenConstants.SOURCES_CLASSIFIER, artifacts, downloadedFiles);
      }

      if (shouldDownload(mySettings.getDownloadJavadoc(), demand)) {
        download(MavenConstants.JAVADOC_CLASSIFIER, artifacts, downloadedFiles);
      }

      if (shouldDownload(mySettings.getDownloadPlugins(), demand)) {
        downloadPlugins();
      }
    }
    finally {
      scheduleFilesRefresh(downloadedFiles);
    }
  }

  private boolean shouldDownload(MavenArtifactSettings.UPDATE_MODE level, boolean demand) {
    return level == MavenArtifactSettings.UPDATE_MODE.ALWAYS || (level == MavenArtifactSettings.UPDATE_MODE.ON_DEMAND && demand);
  }

  private void scheduleFilesRefresh(final List<File> downloadedFiles) {
    Runnable refreshTask = new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshIoFiles(downloadedFiles);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isDispatchThread()) {
      refreshTask.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(refreshTask);
    }
  }

  private Map<Artifact, Set<ArtifactRepository>> collectArtifactsToDownload() {
    Map<Artifact, Set<ArtifactRepository>> result = new TreeMap<Artifact, Set<ArtifactRepository>>();

    for (MavenProjectModel each : myMavenProjects) {
      List<ArtifactRepository> repositories = each.getRepositories();

      for (Artifact eachDependency : each.getDependencies()) {
        if (!MavenConstants.JAR_TYPE.equalsIgnoreCase(eachDependency.getType())) continue;
        if (Artifact.SCOPE_SYSTEM.equalsIgnoreCase(eachDependency.getScope())) continue;
        if (myProjectsTree.findProject(eachDependency) != null) continue;

        Set<ArtifactRepository> registeredRepositories = result.get(eachDependency);
        if (registeredRepositories == null) {
          registeredRepositories = new LinkedHashSet<ArtifactRepository>();
          result.put(eachDependency, registeredRepositories);
        }
        registeredRepositories.addAll(repositories);
      }
    }
    return result;
  }

  private void download(String classifier,
                        Map<Artifact, Set<ArtifactRepository>> libraryArtifacts,
                        List<File> downloadedFiles) throws MavenProcessCanceledException {
    myProgress.setText(ProjectBundle.message("maven.downloading.artifact", classifier));

    int step = 0;
    for (Map.Entry<Artifact, Set<ArtifactRepository>> eachEntry : libraryArtifacts.entrySet()) {
      Artifact eachArtifact = eachEntry.getKey();

      myProgress.checkCanceled();
      myProgress.setFraction(((double)step++) / libraryArtifacts.size());
      myProgress.setText2(eachArtifact.toString());

      Artifact a = myEmbedder.createArtifact(eachArtifact.getGroupId(),
                                             eachArtifact.getArtifactId(),
                                             eachArtifact.getVersion(),
                                             MavenConstants.JAR_TYPE,
                                             classifier);
      myEmbedder.resolve(a, new ArrayList<ArtifactRepository>(eachEntry.getValue()));
      if (a.isResolved()) downloadedFiles.add(a.getFile());
    }
  }

  private void downloadPlugins() throws MavenProcessCanceledException {
    myProgress.setText(ProjectBundle.message("maven.downloading.artifact", "plugins"));

    int pluginsCount = 0;
    for (MavenProjectModel each : myMavenProjects) {
      pluginsCount += each.getPlugins().size();
    }

    int step = 0;
    for (MavenProjectModel eachProject : myMavenProjects) {
      for (Plugin eachPlugin : eachProject.getPlugins()) {
        myProgress.checkCanceled();
        myProgress.setFraction(((double)step++) / pluginsCount);
        myProgress.setText2(eachPlugin.getKey());

        myEmbedder.resolvePlugin(eachPlugin, eachProject.getMavenProject());
        myProjectsTree.fireUpdated(eachProject);
      }
    }
  }
}
