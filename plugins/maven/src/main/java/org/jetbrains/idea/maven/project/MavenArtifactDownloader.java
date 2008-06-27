package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Plugin;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;

import java.io.File;
import java.util.*;

public class MavenArtifactDownloader {
  static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenArtifactDownloader");

  private final MavenArtifactSettings mySettings;
  private final MavenEmbedderWrapper myEmbedder;
  private final MavenProcess myProgress;

  public MavenArtifactDownloader(MavenArtifactSettings settings, MavenEmbedderWrapper embedder, MavenProcess p) {
    mySettings = settings;
    myEmbedder = embedder;
    myProgress = p;
  }

  public void download(List<MavenProjectModel> mavenProjects,
                       boolean demand) throws CanceledException {
    Map<MavenId, Set<ArtifactRepository>> libraryArtifacts = collectLibraryArtifacts(mavenProjects);
    List<File> downloadedFiles = new ArrayList<File>();

    try {
      if (isEnabled(mySettings.getDownloadSources(), demand)) {
        download(libraryArtifacts, MavenConstants.SOURCES_CLASSIFIER, downloadedFiles);
      }

      if (isEnabled(mySettings.getDownloadJavadoc(), demand)) {
        download(libraryArtifacts, MavenConstants.JAVADOC_CLASSIFIER, downloadedFiles);
      }

      if (isEnabled(mySettings.getDownloadPlugins(), demand)) {
        downloadPlugins(mavenProjects);
      }
    }
    finally {
      scheduleFilesRefresh(downloadedFiles);
    }
  }

  private void scheduleFilesRefresh(final List<File> downloadedFiles) {
    Runnable r = new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshIoFiles(downloadedFiles);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(r);
    }
  }

  private boolean isEnabled(MavenArtifactSettings.UPDATE_MODE level, boolean demand) {
    return level == MavenArtifactSettings.UPDATE_MODE.ALWAYS || (level == MavenArtifactSettings.UPDATE_MODE.ON_DEMAND && demand);
  }

  private static Map<MavenId, Set<ArtifactRepository>> collectLibraryArtifacts(List<MavenProjectModel> mavenProjects) {
    Map<MavenId, Set<ArtifactRepository>> result = new TreeMap<MavenId, Set<ArtifactRepository>>();

    for (MavenProjectModel each : mavenProjects) {
      Collection<Artifact> artifacts = each.getDependencies();
      List remoteRepositories = each.getMavenProject().getRemoteArtifactRepositories();

      for (Artifact artifact : artifacts) {
        if (artifact.getType().equalsIgnoreCase(MavenConstants.JAR_TYPE) &&
            !artifact.getScope().equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
          MavenId id = new MavenId(artifact);
          if (!isExistingProject(artifact, mavenProjects)) {
            Set<ArtifactRepository> repos = result.get(id);
            if (repos == null) {
              repos = new HashSet<ArtifactRepository>();
              result.put(id, repos);
            }
            //noinspection unchecked
            repos.addAll(remoteRepositories);
          }
        }
      }
    }
    return result;
  }

  private static boolean isExistingProject(Artifact artifact, List<MavenProjectModel> mavenProjects) {
    for (MavenProjectModel each : mavenProjects) {
      if (each.getMavenId().equals(new MavenId(artifact))) return true;
    }
    return false;
  }

  private void download(Map<MavenId, Set<ArtifactRepository>> libraryArtifacts, String classifier, List<File> downloadedFiles)
      throws CanceledException {
    myProgress.setText(ProjectBundle.message("maven.downloading.artifact", classifier));
    int step = 0;
    for (Map.Entry<MavenId, Set<ArtifactRepository>> entry : libraryArtifacts.entrySet()) {
      myProgress.checkCanceled();

      final MavenId id = entry.getKey();

      myProgress.setFraction(((double)step++) / libraryArtifacts.size());
      myProgress.setText2(id.toString());

      Artifact a = myEmbedder.createArtifact(id.groupId,
                                             id.artifactId,
                                             id.version,
                                             MavenConstants.JAR_TYPE,
                                             classifier);
      myEmbedder.resolve(a, new ArrayList<ArtifactRepository>(entry.getValue()));

      if (a.isResolved()) downloadedFiles.add(a.getFile());
    }
  }

  private void downloadPlugins(List<MavenProjectModel> projects) throws CanceledException {
    myProgress.setText(ProjectBundle.message("maven.downloading.artifact", "plugins"));

    int pluginsCount = 0;
    for (MavenProjectModel each : projects) {
      pluginsCount += each.getPlugins().size();
    }

    int step = 0;
    for (MavenProjectModel eachProject : projects) {
      for (Plugin eachPlugin : eachProject.getPlugins()) {
        myProgress.checkCanceled();
        myProgress.setFraction(((double)step++) / pluginsCount);
        myProgress.setText2(eachPlugin.getKey());
        myEmbedder.resolvePlugin(eachPlugin, eachProject.getMavenProject());
      }
    }
  }
}
