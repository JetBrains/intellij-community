package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
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
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.util.*;

public class MavenArtifactDownloader {
  static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenArtifactDownloader");

  private final MavenArtifactSettings mySettings;
  private final MavenEmbedder myEmbedder;
  private final Progress myProgress;

  public MavenArtifactDownloader(MavenArtifactSettings settings, MavenEmbedder embedder, Progress p) {
    mySettings = settings;
    myEmbedder = embedder;
    myProgress = p;
  }

  public static void download(final Project project) throws CanceledException, MavenException {
    final MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    final MavenImporter importer = MavenImporter.getInstance(project);

    final Map<MavenProject, Collection<String>> mavenProjects = new HashMap<MavenProject, Collection<String>>();

    final Map<VirtualFile, MavenProject> fileToProject = new HashMap<VirtualFile, MavenProject>();
    final MavenEmbedder e = MavenFactory.createEmbedderForRead(MavenCore.getInstance(project).getState());
    try {
      for (VirtualFile file : projectsManager.getFiles()) {
        if (!projectsManager.isIgnored(file)) {
          MavenProject p = projectsManager.getResolvedProject(file);
          if (p == null) continue;
          mavenProjects.put(p, projectsManager.getProfiles(file));
          fileToProject.put(file, p);
        }
      }

      final Map<MavenProject, Module> projectsToModules = new HashMap<MavenProject, Module>();

      for (Module module : ModuleManager.getInstance(project).getModules()) {
        VirtualFile pomFile = importer.findPomForModule(module);
        if (pomFile != null && !projectsManager.isIgnored(pomFile)) {
          MavenProject mavenProject = fileToProject.get(pomFile);
          if (mavenProject != null) {
            projectsToModules.put(mavenProject, module);
          }
        }
      }

      Progress.run(project, ProjectBundle.message("maven.downloading"), new Progress.Process() {
        public void run(Progress p) throws MavenException, CanceledException {
          Collection<ProjectId> projectIds = new ArrayList<ProjectId>();
          for (MavenProject mavenProject : projectsToModules.keySet()) {
            projectIds.add(new ProjectId(mavenProject.getArtifact()));
          }
          new MavenArtifactDownloader(importer.getArtifactSettings(), e, p)
            .download(project, mavenProjects, fileToProject, projectIds, true);
        }
      });
    } finally {
      MavenFactory.releaseEmbedder(e);
    }

    VirtualFileManager.getInstance().refresh(false);
  }

  public void download(Project project,
                       Map<MavenProject, Collection<String>> projectsWithProfiles,
                       Map<VirtualFile, MavenProject> projectToFile,
                       Collection<ProjectId> projectIds,
                       boolean demand) throws CanceledException, MavenException {
    Map<MavenId, Set<ArtifactRepository>> libraryArtifacts = collectLibraryArtifacts(projectsWithProfiles.keySet(), projectIds);

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

      Map<Plugin, MavenProject> plugins = ProjectUtil.collectPlugins(projectsWithProfiles);
      collectAttachedPlugins(projectsManager, projectToFile, plugins);
      downloadPlugins(plugins);
      projectsManager.updateAllFiles();
    }

    myProgress.checkCanceled();

    if (isEnabled(mySettings.getGenerateSources(), demand)) {
      generateSources(project, createGenerateCommand(projectsWithProfiles));
    }
  }

  private void collectAttachedPlugins(MavenProjectsManager projectsManager,  Map<VirtualFile, MavenProject> projectToFile, Map<Plugin, MavenProject> plugins) throws MavenException {
    for(VirtualFile file : projectsManager.getFiles()){
      for(MavenId mavenId : projectsManager.getAttachedPlugins(file)){
        final Plugin plugin = new Plugin();
        plugin.setGroupId(mavenId.groupId);
        plugin.setArtifactId(mavenId.artifactId);
        plugin.setVersion(mavenId.version);
        plugins.put(plugin, projectToFile.get(file));
      }
    }
  }

  private boolean isEnabled(MavenArtifactSettings.UPDATE_MODE level, boolean demand) {
    return level == MavenArtifactSettings.UPDATE_MODE.ALWAYS || (level == MavenArtifactSettings.UPDATE_MODE.ON_DEMAND && demand);
  }

  private static Map<MavenId, Set<ArtifactRepository>> collectLibraryArtifacts(Collection<MavenProject> mavenProjects, Collection<ProjectId> projectIds) {
    Map<MavenId, Set<ArtifactRepository>> result = new TreeMap<MavenId, Set<ArtifactRepository>>();

    for (MavenProject mavenProject : mavenProjects) {
      Collection<Artifact> artifacts = mavenProject.getArtifacts();
      if (artifacts != null) {
        List remoteRepositories = mavenProject.getRemoteArtifactRepositories();

        for (Artifact artifact : artifacts) {
          if (artifact.getType().equalsIgnoreCase(Constants.JAR_TYPE) &&
              !artifact.getScope().equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
            MavenId id = new MavenId(artifact);
            if (!projectIds.contains(id)) {
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
    }
    return result;
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

  private void downloadPlugins(Map<Plugin, MavenProject> plugins) throws CanceledException {
    myProgress.setText(ProjectBundle.message("maven.progress.downloading", "plugins"));

    int step = 0;

    for (Map.Entry<Plugin, MavenProject> entry : plugins.entrySet()) {
      final Plugin plugin = entry.getKey();

      myProgress.checkCanceled();
      myProgress.setFraction(((double)step++) / plugins.size());
      myProgress.setText2(plugin.getKey());

      MavenEmbedderAdapter.verifyPlugin(plugin, entry.getValue(), myEmbedder);
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

  private static List<MavenRunnerParameters> createGenerateCommand(Map<MavenProject, Collection<String>> projects) {
    final List<MavenRunnerParameters> commands = new ArrayList<MavenRunnerParameters>();
    final List<String> goals = Arrays.asList(generateGoals);

    final Set<String> modulePaths = new HashSet<String>();
    for (Map.Entry<MavenProject, Collection<String>> entry : projects.entrySet()) {
      ProjectUtil.collectAbsoluteModulePaths(entry.getKey(), entry.getValue(), modulePaths);
    }

    for (Map.Entry<MavenProject, Collection<String>> entry : projects.entrySet()) {
      final File file = entry.getKey().getFile();
      if (!modulePaths.contains(FileUtil.toSystemIndependentName(file.getParent()))) { // only for top-level projects
        commands.add(new MavenRunnerParameters(file.getPath(), goals, entry.getValue()));
      }
    }
    return commands;
  }
}
