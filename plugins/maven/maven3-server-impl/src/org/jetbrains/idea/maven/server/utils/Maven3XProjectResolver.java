// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import com.intellij.maven.server.telemetry.MavenServerOpenTelemetry;
import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuilder;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenProjectProblem;
import org.jetbrains.idea.maven.model.MavenWorkspaceMap;
import org.jetbrains.idea.maven.server.LongRunningTask;
import org.jetbrains.idea.maven.server.Maven3AetherModelConverter;
import org.jetbrains.idea.maven.server.Maven3EffectivePomDumper;
import org.jetbrains.idea.maven.server.Maven3ImporterSpy;
import org.jetbrains.idea.maven.server.Maven3ModelConverter;
import org.jetbrains.idea.maven.server.Maven3TransferListenerAdapter;
import org.jetbrains.idea.maven.server.Maven3WorkspaceMapReader;
import org.jetbrains.idea.maven.server.Maven3XProfileUtil;
import org.jetbrains.idea.maven.server.Maven3XServerEmbedder;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorImpl;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.jetbrains.idea.maven.server.MavenServerStatsCollector;
import org.jetbrains.idea.maven.server.PomHashMap;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.jetbrains.idea.maven.server.MavenServerEmbedder.MAVEN_EMBEDDER_VERSION;

public class Maven3XProjectResolver {
  protected final @NotNull Maven3XServerEmbedder myEmbedder;
  private final @NotNull MavenServerOpenTelemetry myTelemetry;
  private final boolean myUpdateSnapshots;
  private final @NotNull Maven3ImporterSpy myImporterSpy;
  private final LongRunningTask myLongRunningTask;
  @NotNull List<@NotNull File> myFilesToResolve;
  private final PomHashMap myPomHashMap;
  private final List<String> myActiveProfiles;
  private final List<String> myInactiveProfiles;
  protected final @Nullable MavenWorkspaceMap myWorkspaceMap;
  private final @NotNull Properties userProperties;
  private final boolean myResolveInParallel;

  public Maven3XProjectResolver(@NotNull Maven3XServerEmbedder embedder,
                                @NotNull MavenServerOpenTelemetry telemetry,
                                boolean updateSnapshots,
                                @NotNull Maven3ImporterSpy importerSpy,
                                @NotNull LongRunningTask longRunningTask,
                                @NotNull List<@NotNull File> filesToResolve,
                                @NotNull PomHashMap pomHashMap,
                                @NotNull List<String> activeProfiles,
                                @NotNull List<String> inactiveProfiles,
                                @Nullable MavenWorkspaceMap workspaceMap,
                                @NotNull Properties userProperties,
                                boolean resolveInParallel) {
    myEmbedder = embedder;
    myTelemetry = telemetry;
    myUpdateSnapshots = updateSnapshots;
    myImporterSpy = importerSpy;
    myLongRunningTask = longRunningTask;
    myFilesToResolve = filesToResolve;
    myPomHashMap = pomHashMap;
    myActiveProfiles = activeProfiles;
    myInactiveProfiles = inactiveProfiles;
    myWorkspaceMap = workspaceMap;
    this.userProperties = userProperties;
    myResolveInParallel = resolveInParallel;
  }

  public @NotNull ArrayList<MavenServerExecutionResult> resolveProjects() {
    try {
      return myTelemetry.callWithSpan("doResolveProject", () -> doResolveProject());
    }
    catch (Exception e) {
      throw myEmbedder.wrapToSerializableRuntimeException(e);
    }
  }

  private static class ProjectBuildingResultInfo {
    @NotNull String projectId;
    @NotNull MavenProject mavenProject;
    @NotNull List<ModelProblem> modelProblems;
    @NotNull List<Exception> exceptions;
    String dependencyHash;

    private ProjectBuildingResultInfo(@NotNull String projectId,
                                      @NotNull MavenProject mavenProject,
                                      @NotNull List<ModelProblem> modelProblems,
                                      @NotNull List<Exception> exceptions,
                                      String dependencyHash) {
      this.projectId = projectId;
      this.mavenProject = mavenProject;
      this.modelProblems = modelProblems;
      this.exceptions = exceptions;
      this.dependencyHash = dependencyHash;
    }

    @Override
    public String toString() {
      return "ProjectBuildingResultData{" +
             "projectId=" + projectId +
             ", dependencyHash=" + dependencyHash +
             '}';
    }
  }

  private @NotNull ArrayList<MavenServerExecutionResult> doResolveProject() {
    List<File> files = myFilesToResolve;
    File file = !files.isEmpty() ? files.iterator().next() : null;
    files.forEach(f -> MavenServerStatsCollector.fileRead(f));
    MavenExecutionRequest request = myEmbedder.createRequest(file, myActiveProfiles, myInactiveProfiles, userProperties);

    request.setUpdateSnapshots(myUpdateSnapshots);

    ArrayList<MavenServerExecutionResult> executionResults = new ArrayList<>();

    myEmbedder.executeWithMavenSession(request, () -> {
      executionResults.addAll(getExecutionResults(files, request));
    });

    return executionResults;
  }

  private @NotNull ArrayList<MavenServerExecutionResult> getExecutionResults(Collection<File> files,
                                                                             MavenExecutionRequest request) {
    ArrayList<MavenServerExecutionResult> executionResults = new ArrayList<>();
    try {
      MavenSession mavenSession = myEmbedder.getComponent(LegacySupport.class).getSession();
      RepositorySystemSession repositorySession = myEmbedder.getComponent(LegacySupport.class).getRepositorySession();
      if (repositorySession instanceof DefaultRepositorySystemSession) {
        DefaultRepositorySystemSession session = (DefaultRepositorySystemSession)repositorySession;
        MavenServerConsoleIndicatorImpl indicator = myLongRunningTask.getIndicator();
        myImporterSpy.setIndicator(indicator);
        session.setTransferListener(new Maven3TransferListenerAdapter(indicator));

        setupWorkspaceReader(session);

        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
      }

      List<ProjectBuildingResult> buildingResults = myTelemetry.callWithSpan("getProjectBuildingResults " + files.size(), () ->
        getProjectBuildingResults(request, files));

      fillSessionCache(mavenSession, repositorySession, buildingResults);

      boolean addUnresolved = System.getProperty("idea.maven.no.use.dependency.graph") == null;

      boolean runInParallel = myResolveInParallel;
      Map<File, String> fileToNewDependencyHash = collectHashes(runInParallel, buildingResults);

      List<ProjectBuildingResultInfo> buildingResultInfos = new ArrayList<>();

      for (ProjectBuildingResult buildingResult : buildingResults) {
        MavenProject project = buildingResult.getProject();
        String projectId = buildingResult.getProjectId();
        File pomFile = buildingResult.getPomFile();
        List<ModelProblem> modelProblems = buildingResult.getProblems();

        if (project == null || pomFile == null) {
          executionResults.add(createExecutionResult(pomFile, modelProblems));
          continue;
        }

        boolean hasErrors = false;
        for (ModelProblem p : modelProblems) {
          if (p.getSeverity() == ModelProblem.Severity.ERROR || p.getSeverity() == ModelProblem.Severity.FATAL) {
            hasErrors = true;
            break;
          }
        }
        if (hasErrors) {
          executionResults.add(createExecutionResult(pomFile, Collections.emptyList(), modelProblems, project, null, null, false));
          continue;
        }

        String newDependencyHash = fileToNewDependencyHash.get(pomFile);
        if (!transitiveDependenciesChanged(pomFile, newDependencyHash, fileToNewDependencyHash)) {
          executionResults.add(createExecutionResult(project, newDependencyHash));
          continue;
        }

        List<Exception> exceptions = new ArrayList<>();

        loadExtensions(project, exceptions);

        project.setDependencyArtifacts(project.createArtifacts(myEmbedder.getComponent(ArtifactFactory.class), null, null));

        buildingResultInfos.add(new ProjectBuildingResultInfo(projectId, project, modelProblems, exceptions, newDependencyHash));
      }

      myLongRunningTask.updateTotalRequests(buildingResultInfos.size());
      Collection<MavenServerExecutionResult> execResults =
        myTelemetry.callWithSpan("resolveBuildingResults", () ->
          myTelemetry.execute(
            runInParallel,
            buildingResultInfos, br -> {
              if (myLongRunningTask.isCanceled()) return MavenServerExecutionResult.EMPTY;
              MavenServerExecutionResult result = myTelemetry.callWithSpan(
                "resolveBuildingResult " + br.projectId, () ->
                  resolveBuildingResult(repositorySession, addUnresolved, br.mavenProject, br.modelProblems, br.exceptions,
                                        br.dependencyHash));
              myLongRunningTask.incrementFinishedRequests();
              return result;
            }
          ));

      executionResults.addAll(execResults);
    }
    catch (Exception e) {
      executionResults.add(createExecutionResult(e));
    }
    return executionResults;
  }

  private boolean transitiveDependenciesChanged(@NotNull File pomFile,
                                                String newDependencyHash,
                                                Map<File, String> fileToNewDependencyHash) {
    if (dependenciesChanged(pomFile, newDependencyHash)) return true;
    for (File dependencyPomFile : myPomHashMap.getFileDependencies(pomFile)) {
      if (dependenciesChanged(dependencyPomFile, fileToNewDependencyHash.get(dependencyPomFile))) return true;
    }
    return false;
  }

  private boolean dependenciesChanged(@NotNull File pomFile, String newDependencyHash) {
    String previousDependencyHash = myPomHashMap.getDependencyHash(pomFile);
    return previousDependencyHash == null || !previousDependencyHash.equals(newDependencyHash);
  }

  private @NotNull Map<File, String> collectHashes(boolean runInParallel, List<ProjectBuildingResult> buildingResults) {
    Map<File, String> fileToNewDependencyHash = new ConcurrentHashMap<>();
    myTelemetry.callWithSpan("dependencyHashes", () ->
      myTelemetry.execute(
        runInParallel,
        buildingResults, br -> {
          String newDependencyHash = Maven3EffectivePomDumper.dependencyHash(br.getProject());
          if (null != newDependencyHash) {
            File pomFile = br.getPomFile();
            if (pomFile != null) {
              fileToNewDependencyHash.put(pomFile, newDependencyHash);
            }
          }
          return br;
        }
      ));
    return fileToNewDependencyHash;
  }

  protected void setupWorkspaceReader(DefaultRepositorySystemSession session) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "3.3.1") < 0) return;
    if (myWorkspaceMap != null) {
      session.setWorkspaceReader(new Maven3WorkspaceMapReader(myWorkspaceMap, myEmbedder.getSystemProperties()));
    }
  }

  private @NotNull MavenServerExecutionResult resolveBuildingResult(RepositorySystemSession repositorySession,
                                                                    boolean addUnresolved,
                                                                    MavenProject project,
                                                                    @NotNull List<ModelProblem> modelProblems,
                                                                    List<Exception> exceptions,
                                                                    String dependencyHash) {
    try {
      DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
      Set<Artifact> artifacts = resolveArtifacts(dependencyResolutionResult, addUnresolved);
      project.setArtifacts(artifacts);

      return createExecutionResult(exceptions, modelProblems, project, dependencyResolutionResult, dependencyHash);
    }
    catch (Exception e) {
      return createExecutionResult(project, e);
    }
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(@NotNull MavenProject mavenProject, String dependencyHash) {
    return createExecutionResult(mavenProject.getFile(), Collections.emptyList(), Collections.emptyList(), mavenProject, null,
                                 dependencyHash, true);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(Exception exception) {
    return createExecutionResult(null, exception);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(MavenProject mavenProject, Exception exception) {
    return createExecutionResult(Collections.singletonList(exception), Collections.emptyList(), mavenProject, null, null);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(List<Exception> exceptions,
                                                                    List<ModelProblem> modelProblems,
                                                                    MavenProject mavenProject,
                                                                    DependencyResolutionResult dependencyResolutionResult,
                                                                    String dependencyHash) {
    return createExecutionResult(null, exceptions, modelProblems, mavenProject, dependencyResolutionResult, dependencyHash, false);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(@Nullable File file, List<ModelProblem> modelProblems) {
    return createExecutionResult(file, Collections.emptyList(), modelProblems, null, null, null, false);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(@Nullable File file,
                                                                    @NotNull List<Exception> exceptions,
                                                                    @NotNull List<ModelProblem> modelProblems,
                                                                    @Nullable MavenProject mavenProject,
                                                                    DependencyResolutionResult dependencyResolutionResult,
                                                                    String dependencyHash,
                                                                    boolean dependencyResolutionSkipped) {
    if (null != dependencyResolutionResult && null != dependencyResolutionResult.getCollectionErrors()) {
      exceptions.addAll(dependencyResolutionResult.getCollectionErrors());
    }

    if (null == file && null != mavenProject) {
      file = mavenProject.getFile();
    }

    Collection<MavenProjectProblem> problems = myEmbedder.collectProblems(file, exceptions, modelProblems);

    if (mavenProject == null) return new MavenServerExecutionResult(file, null, problems, Collections.emptySet());

    MavenModel model = new MavenModel();
    try {
      DependencyNode dependencyGraph = dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

      List<DependencyNode> dependencyNodes =
        dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
      model = Maven3AetherModelConverter.convertModelWithAetherDependencyTree(
        mavenProject.getModel(),
        mavenProject.getCompileSourceRoots(),
        mavenProject.getTestCompileSourceRoots(),
        mavenProject.getArtifacts(),
        dependencyNodes,
        mavenProject.getPluginArtifacts(),
        mavenProject.getExtensionArtifacts(),
        myEmbedder.getLocalRepositoryFile());
    }
    catch (Exception e) {
      problems.addAll(myEmbedder.collectProblems(mavenProject.getFile(), Collections.singleton(e), modelProblems));
    }

    Collection<String> activatedProfiles = Maven3XProfileUtil.collectActivatedProfiles(mavenProject);

    Map<String, String> mavenModelMap = Maven3ModelConverter.convertToMap(mavenProject.getModel());
    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, getManagedDependencies(mavenProject), dependencyHash, dependencyResolutionSkipped,
                                                 mavenModelMap, activatedProfiles);
    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<>();
    collectUnresolvedArtifactProblems(file, dependencyResolutionResult, unresolvedProblems);
    return new MavenServerExecutionResult(file, data, problems, Collections.emptySet(), unresolvedProblems);
  }

  private static @NotNull List<MavenId> getManagedDependencies(@Nullable MavenProject project) {

    if (project == null ||
        project.getDependencyManagement() == null ||
        project.getDependencyManagement().getDependencies() == null) {
      return Collections.emptyList();
    }
    //noinspection SSBasedInspection
    return project.getDependencyManagement().getDependencies().stream().map(
      dep -> new MavenId(dep.getGroupId(), dep.getArtifactId(), dep.getVersion())
    ).collect(Collectors.toList());
  }

  private static void fillSessionCache(MavenSession mavenSession,
                                       RepositorySystemSession session,
                                       List<ProjectBuildingResult> buildingResults) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "3.3.1") < 0) return;
    if (session instanceof DefaultRepositorySystemSession) {
      int initialCapacity = (int)(buildingResults.size() * 1.5);
      Map<MavenId, Model> cacheMavenModelMap = new HashMap<>(initialCapacity);
      Map<String, MavenProject> mavenProjectMap = new HashMap<>(initialCapacity);
      for (ProjectBuildingResult result : buildingResults) {
        if (result.getProblems() != null && !result.getProblems().isEmpty()) continue;
        Model model = result.getProject().getModel();
        String key = ArtifactUtils.key(model.getGroupId(), model.getArtifactId(), model.getVersion());
        mavenProjectMap.put(key, result.getProject());
        cacheMavenModelMap.put(new MavenId(model.getGroupId(), model.getArtifactId(), model.getVersion()), model);
      }
      mavenSession.setProjectMap(mavenProjectMap);
      DefaultRepositorySystemSession defaultSession = (DefaultRepositorySystemSession)session;
      WorkspaceReader reader = defaultSession.getWorkspaceReader();
      if (reader instanceof Maven3WorkspaceMapReader) {
        Maven3WorkspaceMapReader mapReader = (Maven3WorkspaceMapReader)reader;
        mapReader.fillSessionCache(cacheMavenModelMap);
      }
    }
  }

  protected @NotNull List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Collection<File> files) {
    ProjectBuilder builder = myEmbedder.getComponent(ProjectBuilder.class);

    ModelInterpolator modelInterpolator = myEmbedder.getComponent(ModelInterpolator.class);

    String savedLocalRepository = null;
    if (modelInterpolator instanceof CustomMaven3ModelInterpolator2) {
      CustomMaven3ModelInterpolator2 customMaven3ModelInterpolator2 = (CustomMaven3ModelInterpolator2)modelInterpolator;
      savedLocalRepository = customMaven3ModelInterpolator2.getLocalRepository();
      customMaven3ModelInterpolator2.setLocalRepository(request.getLocalRepositoryPath().getAbsolutePath());
    }


    List<ProjectBuildingResult> buildingResults = new ArrayList<>();

    ProjectBuildingRequest projectBuildingRequest = request.getProjectBuildingRequest();
    projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    projectBuildingRequest.setResolveDependencies(false);

    try {
      try {
        buildMultiplyPoms(builder, buildingResults, projectBuildingRequest, files);
      }
      catch (ProjectBuildingException e) {
        for (ProjectBuildingResult result : e.getResults()) {
          if (result.getProject() != null) {
            buildingResults.add(result);
          }
          else {
            buildSinglePom(builder, buildingResults, projectBuildingRequest, result.getPomFile());
          }
        }
      }
    }
    finally {
      if (modelInterpolator instanceof CustomMaven3ModelInterpolator2 && savedLocalRepository != null) {
        ((CustomMaven3ModelInterpolator2)modelInterpolator).setLocalRepository(savedLocalRepository);
      }
    }
    return buildingResults;
  }

  protected void buildMultiplyPoms(@NotNull ProjectBuilder builder,
                                   List<ProjectBuildingResult> buildingResults,
                                   ProjectBuildingRequest projectBuildingRequest,
                                   @NotNull Collection<File> files
  ) throws ProjectBuildingException {
    buildingResults.addAll(builder.build(new ArrayList<>(files), true, projectBuildingRequest));
  }

  protected void buildSinglePom(ProjectBuilder builder,
                                List<ProjectBuildingResult> buildingResults,
                                ProjectBuildingRequest projectBuildingRequest,
                                File pomFile) {
    try {
      ProjectBuildingResult build = builder.build(pomFile, projectBuildingRequest);
      buildingResults.add(build);
    }
    catch (ProjectBuildingException e) {
      Maven3ResolverUtil.handleProjectBuildingException(buildingResults, e);
    }
  }

  /**
   * adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
   */
  private void loadExtensions(MavenProject project, List<Exception> exceptions) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants = getLifecycleParticipants(Collections.singletonList(project));
    if (!lifecycleParticipants.isEmpty()) {
      LegacySupport legacySupport = myEmbedder.getComponent(LegacySupport.class);
      MavenSession session = legacySupport.getSession();
      if (null != session) {
        session.setCurrentProject(project);
        try {
          // the method can be removed
          session.setAllProjects(Collections.singletonList(project));
        }
        catch (NoSuchMethodError ignore) {
        }
        session.setProjects(Collections.singletonList(project));

        for (AbstractMavenLifecycleParticipant listener : lifecycleParticipants) {
          Thread.currentThread().setContextClassLoader(listener.getClass().getClassLoader());
          try {
            listener.afterProjectsRead(session);
          }
          catch (Exception e) {
            exceptions.add(e);
          }
          finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
          }
        }
      }
    }
  }

  /**
   * copied from {@link DefaultProjectBuilder#resolveDependencies(MavenProject, RepositorySystemSession)}
   */
  private DependencyResolutionResult resolveDependencies(MavenProject project, RepositorySystemSession session) {
    DependencyResolutionResult resolutionResult;

    try {
      ProjectDependenciesResolver dependencyResolver = myEmbedder.getComponent(ProjectDependenciesResolver.class);
      DefaultDependencyResolutionRequest resolution = new DefaultDependencyResolutionRequest(project, session);
      resolutionResult = dependencyResolver.resolve(resolution);
    }
    catch (DependencyResolutionException e) {
      resolutionResult = e.getResult();
    }

    Set<Artifact> artifacts = new LinkedHashSet<>();
    if (resolutionResult.getDependencyGraph() != null) {
      RepositoryUtils.toArtifacts(artifacts, resolutionResult.getDependencyGraph().getChildren(),
                                  Collections.singletonList(project.getArtifact().getId()), null);

      // Maven 2.x quirk: an artifact always points at the local repo, regardless whether resolved or not
      LocalRepositoryManager lrm = session.getLocalRepositoryManager();
      for (Artifact artifact : artifacts) {
        if (!artifact.isResolved()) {
          String path = lrm.getPathForLocalArtifact(RepositoryUtils.toArtifact(artifact));
          artifact.setFile(new File(lrm.getRepository().getBasedir(), path));
        }
      }
    }
    project.setResolvedArtifacts(artifacts);
    project.setArtifacts(artifacts);

    return resolutionResult;
  }

  private @NotNull Set<Artifact> resolveArtifacts(DependencyResolutionResult dependencyResolutionResult, boolean addUnresolvedNodes) {
    Map<Dependency, Artifact> winnerDependencyMap = new IdentityHashMap<>();
    Set<Artifact> artifacts = new LinkedHashSet<>();
    Set<Dependency> addedDependencies = Collections.newSetFromMap(new IdentityHashMap<>());
    resolveConflicts(dependencyResolutionResult, winnerDependencyMap);

    if (dependencyResolutionResult.getDependencyGraph() != null) {
      dependencyResolutionResult.getDependencyGraph().getChildren();
    }

    for (Dependency dependency : dependencyResolutionResult.getDependencies()) {
      Artifact artifact = dependency == null ? null : winnerDependencyMap.get(dependency);
      if (artifact != null) {
        addedDependencies.add(dependency);
        artifacts.add(artifact);
        resolveAsModule(artifact);
      }
    }

    //if any syntax error presents in pom.xml we may not get dependencies via getDependencies, but they are in dependencyGraph.
    // we need to BFS this graph and add dependencies
    if (addUnresolvedNodes) {
      Queue<DependencyNode> queue =
        new ArrayDeque<>(dependencyResolutionResult.getDependencyGraph().getChildren());
      while (!queue.isEmpty()) {
        DependencyNode node = queue.poll();
        queue.addAll(node.getChildren());
        Dependency dependency = node.getDependency();
        if (dependency == null || !addedDependencies.add(dependency)) {
          continue;
        }
        Artifact artifact = winnerDependencyMap.get(dependency);
        if (artifact != null) {
          addedDependencies.add(dependency);
          //todo: properly resolve order
          artifacts.add(artifact);
          resolveAsModule(artifact);
        }
      }
    }

    return artifacts;
  }

  private static void resolveConflicts(DependencyResolutionResult dependencyResolutionResult,
                                       Map<Dependency, Artifact> winnerDependencyMap) {
    dependencyResolutionResult.getDependencyGraph().accept(new TreeDependencyVisitor(new DependencyVisitor() {
      @Override
      public boolean visitEnter(DependencyNode node) {
        Object winner = node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        Dependency dependency = node.getDependency();
        if (dependency != null && winner == null) {
          Artifact winnerArtifact = Maven3AetherModelConverter.toArtifact(dependency);
          winnerDependencyMap.put(dependency, winnerArtifact);
        }
        return true;
      }

      @Override
      public boolean visitLeave(DependencyNode node) {
        return true;
      }
    }));
  }

  private boolean resolveAsModule(Artifact a) {
    MavenWorkspaceMap map = myWorkspaceMap;
    if (map == null) return false;

    MavenWorkspaceMap.Data resolved = map.findFileAndOriginalId(Maven3ModelConverter.createMavenId(a));
    if (resolved == null) return false;

    a.setResolved(true);
    a.setFile(resolved.getFile(a.getType()));
    a.selectVersion(resolved.originalId.getVersion());
    return true;
  }

  private void collectUnresolvedArtifactProblems(@Nullable File file,
                                                 @Nullable DependencyResolutionResult result,
                                                 Collection<MavenProjectProblem> problems) {
    if (result == null) return;
    String path = file == null ? "" : file.getPath();
    for (Dependency unresolvedDependency : result.getUnresolvedDependencies()) {
      for (Exception exception : result.getResolutionErrors(unresolvedDependency)) {
        String message = getRootMessage(exception);
        Artifact artifact = RepositoryUtils.toArtifact(unresolvedDependency.getArtifact());
        MavenArtifact mavenArtifact = Maven3ModelConverter.convertArtifact(artifact, myEmbedder.getLocalRepositoryFile());
        problems.add(MavenProjectProblem.createUnresolvedArtifactProblem(path, message, false, mavenArtifact));
        break;
      }
    }
  }

  private static @NotNull String getRootMessage(Throwable each) {
    String baseMessage = each.getMessage() != null ? each.getMessage() : "";
    Throwable rootCause = ExceptionUtils.getRootCause(each);
    String rootMessage = rootCause != null ? rootCause.getMessage() : "";
    return StringUtils.isNotEmpty(rootMessage) ? rootMessage : baseMessage;
  }

  /**
   * adapted from {@link DefaultMaven#getLifecycleParticipants(Collection)}
   */
  private Collection<AbstractMavenLifecycleParticipant> getLifecycleParticipants(Collection<MavenProject> projects) {
    Collection<AbstractMavenLifecycleParticipant> lifecycleListeners = new LinkedHashSet<>();

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      lifecycleListeners.addAll(myEmbedder.getComponents(AbstractMavenLifecycleParticipant.class));

      Collection<ClassLoader> scannedRealms = new HashSet<>();

      for (MavenProject project : projects) {
        ClassLoader projectRealm = project.getClassRealm();

        if (projectRealm != null && scannedRealms.add(projectRealm)) {
          Thread.currentThread().setContextClassLoader(projectRealm);

          lifecycleListeners.addAll(myEmbedder.getComponents(AbstractMavenLifecycleParticipant.class));
        }
      }
    }
    finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    return lifecycleListeners;
  }
}
