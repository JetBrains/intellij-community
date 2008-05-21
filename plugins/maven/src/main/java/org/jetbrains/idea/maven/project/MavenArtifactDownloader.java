package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderAdapter;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Plugin;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.embedder.EmbedderFactory;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.util.*;

public class MavenArtifactDownloader {
  static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenArtifactDownloader");

  private final MavenArtifactSettings mySettings;
  private final MavenEmbedder myEmbedder;
  private final MavenProgress myProgress;

  public MavenArtifactDownloader(MavenArtifactSettings settings, MavenEmbedder embedder, MavenProgress p) {
    mySettings = settings;
    myEmbedder = embedder;
    myProgress = p;
  }


  public static void download(final Project project) throws CanceledException, MavenException {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    final MavenEmbedder e = EmbedderFactory.createEmbedderForExecute(MavenCore.getInstance(project).getState());
    try {
      MavenProgress.run(project, ProjectBundle.message("maven.downloading"), new MavenProgress.MavenTask() {
        public void run(MavenProgress p) throws MavenException, CanceledException {
          new MavenArtifactDownloader(manager.getArtifactSettings(), e, p)
              .download(project, manager.getExistingProjects(), true);
        }
      });
    }
    finally {
      EmbedderFactory.releaseEmbedder(e);
    }

    VirtualFileManager.getInstance().refresh(false);
  }

  public void download(Project project,
                       List<MavenProjectModel.Node> mavenProjects,
                       boolean demand) throws CanceledException, MavenException {
    Map<MavenId, Set<ArtifactRepository>> libraryArtifacts = collectLibraryArtifacts(mavenProjects);

    myProgress.checkCanceled();

    if (isEnabled(mySettings.getDownloadSources(), demand)) {
      download(libraryArtifacts, Constants.SOURCES_CLASSIFIER);
    }

    myProgress.checkCanceled();

    if (isEnabled(mySettings.getDownloadJavadoc(), demand)) {
      download(libraryArtifacts, Constants.JAVADOC_CLASSIFIER);
    }

    myProgress.checkCanceled();

    if (isEnabled(mySettings.getDownloadPlugins(), demand)) {
      MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

      Map<Plugin, MavenProjectModel.Node> plugins = ProjectUtil.collectPlugins(mavenProjects);
      downloadPlugins(plugins);
      projectsManager.updateAllFiles();
    }

    myProgress.checkCanceled();

    if (isEnabled(mySettings.getGenerateSources(), demand)) {
      generateSources(project, createGenerateCommand(mavenProjects));
    }
  }

  private boolean isEnabled(MavenArtifactSettings.UPDATE_MODE level, boolean demand) {
    return level == MavenArtifactSettings.UPDATE_MODE.ALWAYS || (level == MavenArtifactSettings.UPDATE_MODE.ON_DEMAND && demand);
  }

  private static Map<MavenId, Set<ArtifactRepository>> collectLibraryArtifacts(List<MavenProjectModel.Node> mavenProjects) {
    Map<MavenId, Set<ArtifactRepository>> result = new TreeMap<MavenId, Set<ArtifactRepository>>();

    for (MavenProjectModel.Node each : mavenProjects) {
      Collection<Artifact> artifacts = each.extractDependencies();
      List remoteRepositories = each.getMavenProject().getRemoteArtifactRepositories();

      for (Artifact artifact : artifacts) {
        if (artifact.getType().equalsIgnoreCase(Constants.JAR_TYPE) &&
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

  private static boolean isExistingProject(Artifact artifact, List<MavenProjectModel.Node> mavenProjects) {
    for (MavenProjectModel.Node each : mavenProjects) {
      if (new ProjectId(artifact).equals(each.getProjectId())) return true;
    }
    return false;
  }

  private void download(Map<MavenId, Set<ArtifactRepository>> libraryArtifacts, String classifier) throws CanceledException {
    myProgress.setText(ProjectBundle.message("maven.progress.downloading", classifier));
    int step = 0;
    for (Map.Entry<MavenId, Set<ArtifactRepository>> entry : libraryArtifacts.entrySet()) {
      myProgress.checkCanceled();

      final MavenId id = entry.getKey();

      myProgress.setFraction(((double)step++) / libraryArtifacts.size());
      myProgress.setText2(id.toString());

      try {
        Artifact a = myEmbedder.createArtifactWithClassifier(id.groupId,
                                                             id.artifactId,
                                                             id.version,
                                                             Constants.JAR_TYPE,
                                                             classifier);
        List<ArtifactRepository> remoteRepos = new ArrayList<ArtifactRepository>(entry.getValue());
        myEmbedder.resolve(a, remoteRepos, myEmbedder.getLocalRepository());
      }
      catch (ArtifactResolutionException ignore) {
      }
      catch (ArtifactNotFoundException ignore) {
      }
      catch (Exception e) {
        LOG.warn("Exception during artifact resolution", e);
      }
    }
  }

  private void downloadPlugins(Map<Plugin, MavenProjectModel.Node> plugins) throws CanceledException {
    myProgress.setText(ProjectBundle.message("maven.progress.downloading", "plugins"));

    int step = 0;

    for (Map.Entry<Plugin, MavenProjectModel.Node> each : plugins.entrySet()) {
      final Plugin plugin = each.getKey();

      myProgress.checkCanceled();
      myProgress.setFraction(((double)step++) / plugins.size());
      myProgress.setText2(plugin.getKey());

      MavenEmbedderAdapter.verifyPlugin(plugin, each.getValue().getMavenProject(), myEmbedder);
    }
  }

  private static void generateSources(Project project, List<MavenRunnerParameters> commands) {
    final MavenCore core = project.getComponent(MavenCore.class);
    final MavenRunner runner = project.getComponent(MavenRunner.class);

    final MavenCoreSettings coreSettings = core.getState().clone();
    coreSettings.setFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_NEVER);
    coreSettings.setNonRecursive(false);

    MavenRunnerSettings runnerSettings = runner.getState().clone();
    runnerSettings.setRunMavenInBackground(false);

    runner.runBatch(commands, coreSettings, runnerSettings, ProjectBundle.message("maven.import.generating.sources"));
  }

  @NonNls private final static String[] generateGoals =
      {"clean", "generate-sources", "generate-resources", "generate-test-sources", "generate-test-resources"};

  private static List<MavenRunnerParameters> createGenerateCommand(List<MavenProjectModel.Node> projects) {
    final List<MavenRunnerParameters> commands = new ArrayList<MavenRunnerParameters>();
    final List<String> goals = Arrays.asList(generateGoals);

    final LinkedHashSet<String> modulePaths = new LinkedHashSet<String>();
    for (MavenProjectModel.Node each : projects) {
      modulePaths.addAll(each.getModulePaths());
    }

    for (MavenProjectModel.Node each : projects) {
      VirtualFile file = each.getFile();
      // skip modules
      if (modulePaths.contains(file.getPath())) continue;

      commands.add(new MavenRunnerParameters(file.getPath(), goals, each.getProfiles()));
    }
    return commands;
  }
}
