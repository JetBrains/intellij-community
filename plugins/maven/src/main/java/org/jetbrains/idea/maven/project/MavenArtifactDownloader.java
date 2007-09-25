package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.builder.MavenBuilder;
import org.jetbrains.idea.maven.builder.MavenBuilderState;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreState;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.File;
import java.util.*;

public class MavenArtifactDownloader {
  static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenArtifactDownloader");

  private final MavenArtifactPreferences myPreferences;
  private final MavenEmbedder myEmbedder;
  private final ProgressIndicator myProgressIndicator;

  public MavenArtifactDownloader(MavenArtifactPreferences preferences, MavenEmbedder embedder, ProgressIndicator progressIndicator) {
    myPreferences = preferences;
    myEmbedder = embedder;
    myProgressIndicator = progressIndicator;
  }

  public static void download(final Project project) {
    final MavenProjectsState projectsState = project.getComponent(MavenProjectsState.class);
    final MavenImporter importer = project.getComponent(MavenImporter.class);

    final Map<MavenProject, Collection<String>> mavenProjects = new HashMap<MavenProject, Collection<String>>();

    for (VirtualFile file : projectsState.getFiles()) {
      if (!projectsState.isIgnored(file)) {
        MavenProject mavenProject = projectsState.getMavenProject(file);
        if (mavenProject != null) {
          mavenProjects.put(mavenProject, projectsState.getProfiles(file));
        }
      }
    }

    final Map<MavenProject, Module> projectsToModules = new HashMap<MavenProject, Module>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      VirtualFile pomFile = importer.findPomForModule(module);
      if (pomFile != null && !projectsState.isIgnored(pomFile)) {
        MavenProject mavenProject = projectsState.getMavenProject(pomFile);
        if (mavenProject != null) {
          projectsToModules.put(mavenProject, module);
        }
      }
    }

    try {
      final MavenEmbedder mavenEmbedder = project.getComponent(MavenCore.class).getState().createEmbedder();

      ProgressManager.getInstance().run(new Task.Modal(project, ProjectBundle.message("maven.title.downloading"), true) {
        public void run(ProgressIndicator indicator) {
          Collection<MavenId> moduleIds = new ArrayList<MavenId>();
          for (MavenProject mavenProject : projectsToModules.keySet()) {
            moduleIds.add(new MavenId(mavenProject.getArtifact()));
          }
          new MavenArtifactDownloader(importer.getArtifactPreferences(), mavenEmbedder, indicator)
            .download(project, mavenProjects, moduleIds, true);
        }
      });

      MavenEnv.releaseEmbedder(mavenEmbedder);
    }
    catch (MavenEmbedderException e) {
      LOG.info("Maven Embedder initialization failed: " + e.getMessage());
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Map.Entry<MavenProject, Module> entry : projectsToModules.entrySet()) {
          MavenToIdeaConverter.updateModel(entry.getValue(), entry.getKey());
        }
      }
    });

    VirtualFileManager.getInstance().refresh(false);
  }

  void download(Project project,
                Map<MavenProject, Collection<String>> mavenProjects,
                Collection<MavenId> mappedToModules,
                boolean demand) {
    final MavenProjectsState projectsState = project.getComponent(MavenProjectsState.class);
    final Map<MavenId, Set<ArtifactRepository>> libraryArtifacts = collectLibraryArtifacts(projectsState, mavenProjects.keySet(), mappedToModules);

    if (myProgressIndicator != null && myProgressIndicator.isCanceled()) return;

    if (isEnabled(myPreferences.getDownloadSources(), demand)) {
      download(libraryArtifacts, MavenToIdeaConverter.SOURCES_CLASSIFIER);
    }

    if (myProgressIndicator != null && myProgressIndicator.isCanceled()) return;

    if (isEnabled(myPreferences.getDownloadJavadoc(), demand)) {
      download(libraryArtifacts, MavenToIdeaConverter.JAVADOC_CLASSIFIER);
    }

    if (myProgressIndicator != null && myProgressIndicator.isCanceled()) return;

    if (isEnabled(myPreferences.getDownloadPlugins(), demand)) {
      final Map<Plugin, MavenProject> plugins = ProjectUtil.collectPlugins(mavenProjects);
      collectAttachedPlugins(projectsState, plugins);
      downloadPlugins(plugins);
      projectsState.updateAllFiles();
    }

    if (myProgressIndicator != null && myProgressIndicator.isCanceled()) return;

    if (isEnabled(myPreferences.getGenerateSources(), demand)) {
      generateSources(project, createGenerateCommand(mavenProjects));
    }
  }

  private void collectAttachedPlugins(final MavenProjectsState projectsState, final Map<Plugin, MavenProject> plugins) {
    for(VirtualFile file : projectsState.getFiles()){
      for(MavenId mavenId : projectsState.getAttachedPlugins(file)){
        final Plugin plugin = new Plugin();
        plugin.setGroupId(mavenId.groupId);
        plugin.setArtifactId(mavenId.artifactId);
        plugin.setVersion(mavenId.version);
        plugins.put(plugin,projectsState.getMavenProject(file));
      }
    }
  }

  private boolean isEnabled(final MavenArtifactPreferences.UPDATE_MODE level, final boolean demand) {
    return level == MavenArtifactPreferences.UPDATE_MODE.ALWAYS || (level == MavenArtifactPreferences.UPDATE_MODE.ON_DEMAND && demand);
  }

  static Map<MavenId, Set<ArtifactRepository>> collectLibraryArtifacts(MavenProjectsState projectsState,
                                                                       Collection<MavenProject> mavenProjects,
                                                                       Collection<MavenId> mappedToModules) {
    final Map<MavenId, Set<ArtifactRepository>> repositoryArtifacts = new TreeMap<MavenId, Set<ArtifactRepository>>();

    for (MavenProject mavenProject : mavenProjects) {
      VirtualFile file = projectsState.getFile(mavenProject);
      if (file != null) {
        Collection<Artifact> artifacts = projectsState.getArtifacts(file);
        if (artifacts != null) {
          final List remoteRepositories = mavenProject.getRemoteArtifactRepositories();
          for (Artifact artifact : artifacts) {
            if (artifact.getType().equalsIgnoreCase(MavenToIdeaConverter.JAR_TYPE) &&
                !artifact.getScope().equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
              MavenId id = new MavenId(artifact);
              if (!mappedToModules.contains(id)) {
                Set<ArtifactRepository> repos = repositoryArtifacts.get(id);
                if (repos == null) {
                  repos = new HashSet<ArtifactRepository>();
                  repositoryArtifacts.put(id, repos);
                }
                //noinspection unchecked
                repos.addAll(remoteRepositories);
              }
            }
          }
        }
      }
    }
    return repositoryArtifacts;
  }

  void download(final Map<MavenId, Set<ArtifactRepository>> libraryArtifacts, final String classifier) {
    if (myProgressIndicator != null) myProgressIndicator.setText(ProjectBundle.message("maven.progress.downloading", classifier));
    int step = 0;
    for (Map.Entry<MavenId, Set<ArtifactRepository>> entry : libraryArtifacts.entrySet()) {
      if (myProgressIndicator != null && myProgressIndicator.isCanceled()) return;
      final MavenId id = entry.getKey();
      if (myProgressIndicator != null) {
        myProgressIndicator.setFraction(((double)step++) / libraryArtifacts.size());
        myProgressIndicator.setText2(id.toString());
      }
      try {
        myEmbedder.resolve(
          myEmbedder.createArtifactWithClassifier(id.groupId, id.artifactId, id.version, MavenToIdeaConverter.JAR_TYPE, classifier),
          new ArrayList<ArtifactRepository>(entry.getValue()), myEmbedder.getLocalRepository());
      }
      catch (ArtifactResolutionException ignore) {
      }
      catch (ArtifactNotFoundException ignore) {
      }
      catch (CancelChecker.CancelledException e) {
        return;
      }
      catch (Exception e) {
        LOG.warn("Exception during artifact resolution", e);
      }
    }
  }

  private void downloadPlugins(final Map<Plugin, MavenProject> plugins) {
    if (myProgressIndicator != null) myProgressIndicator.setText(ProjectBundle.message("maven.progress.downloading", "plugins"));

    int step = 0;

    for (Map.Entry<Plugin, MavenProject> entry : plugins.entrySet()) {
      final Plugin plugin = entry.getKey();
      if (myProgressIndicator != null){
        myProgressIndicator.setFraction(((double)step++) / plugins.size());
        if (myProgressIndicator.isCanceled()) {
          return;
        }
        myProgressIndicator.setText2(plugin.getKey());
      }
      MavenEmbedderAdapter.verifyPlugin(plugin, entry.getValue(), myEmbedder);
    }
  }


  private static void generateSources(Project project, final List<MavenBuildParameters> commands) {
    final MavenCore core = project.getComponent(MavenCore.class);
    final MavenBuilder builder = project.getComponent(MavenBuilder.class);

    final MavenCoreState coreState = core.getState().clone();
    coreState.setFailureBehavior(MavenExecutionRequest.REACTOR_FAIL_NEVER);
    coreState.setNonRecursive(false);

    final MavenBuilderState builderState = builder.getState().clone();
    builderState.setRunMavenInBackground(false);

    builder.runBatch(commands, coreState, builderState, ProjectBundle.message("maven.import.generating.sources"));
  }

  @NonNls private final static String[] generateGoals =
    {"clean", "generate-sources", "generate-resources", "generate-test-sources", "generate-test-resources"};

  private static List<MavenBuildParameters> createGenerateCommand(Map<MavenProject, Collection<String>> projects) {
    final List<MavenBuildParameters> commands = new ArrayList<MavenBuildParameters>();
    final List<String> goals = Arrays.asList(generateGoals);

    final Set<String> modulePaths = new HashSet<String>();
    for (Map.Entry<MavenProject, Collection<String>> entry : projects.entrySet()) {
      ProjectUtil.collectAbsoluteModulePaths(entry.getKey(), entry.getValue(), modulePaths);
    }

    for (Map.Entry<MavenProject, Collection<String>> entry : projects.entrySet()) {
      final File file = entry.getKey().getFile();
      if (!modulePaths.contains(FileUtil.toSystemIndependentName(file.getParent()))) { // only for top-level projects
        commands.add(new MavenBuildParameters(file.getPath(), goals, entry.getValue()));
      }
    }
    return commands;
  }
}
