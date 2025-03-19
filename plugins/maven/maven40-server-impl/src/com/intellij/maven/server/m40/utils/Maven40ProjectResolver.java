// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.maven.server.m40.Maven40ServerEmbedderImpl;
import com.intellij.maven.server.telemetry.MavenServerOpenTelemetry;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.project.*;
import org.apache.maven.resolver.MavenChainedWorkspaceReader;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.LongRunningTask;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.PomHashMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Maven40ProjectResolver {
  private final @NotNull Maven40ServerEmbedderImpl myEmbedder;
  private final @NotNull MavenServerOpenTelemetry myTelemetry;
  private final boolean myUpdateSnapshots;
  private final @NotNull Maven40ImporterSpy myImporterSpy;
  private final LongRunningTask myLongRunningTask;
  private final PomHashMap myPomHashMap;
  private final List<String> myActiveProfiles;
  private final List<String> myInactiveProfiles;
  private final @Nullable MavenWorkspaceMap myWorkspaceMap;
  private final @NotNull File myLocalRepositoryFile;
  private final @NotNull Properties userProperties;
  private final boolean myResolveInParallel;

  public Maven40ProjectResolver(@NotNull Maven40ServerEmbedderImpl embedder,
                                @NotNull MavenServerOpenTelemetry telemetry,
                                boolean updateSnapshots,
                                @NotNull Maven40ImporterSpy importerSpy,
                                @NotNull LongRunningTask longRunningTask,
                                @NotNull PomHashMap pomHashMap,
                                @NotNull List<String> activeProfiles,
                                @NotNull List<String> inactiveProfiles,
                                @Nullable MavenWorkspaceMap workspaceMap,
                                @NotNull File localRepositoryFile,
                                @NotNull Properties userProperties,
                                boolean resolveInParallel) {
    myEmbedder = embedder;
    myTelemetry = telemetry;
    myUpdateSnapshots = updateSnapshots;
    myImporterSpy = importerSpy;
    myLongRunningTask = longRunningTask;
    myPomHashMap = pomHashMap;
    myActiveProfiles = activeProfiles;
    myInactiveProfiles = inactiveProfiles;
    myWorkspaceMap = workspaceMap;
    myLocalRepositoryFile = localRepositoryFile;
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
    Set<File> files = myPomHashMap.keySet();
    File file = !files.isEmpty() ? files.iterator().next() : null;
    MavenExecutionRequest request = myEmbedder.createRequest(file, myActiveProfiles, myInactiveProfiles, userProperties);

    request.setUpdateSnapshots(myUpdateSnapshots);

    ArrayList<MavenServerExecutionResult> executionResults = new ArrayList<>();

    myEmbedder.executeWithMavenSession(request, myWorkspaceMap, myLongRunningTask.getIndicator(), session -> {
      executionResults.addAll(getExecutionResults(session, files, request));
    });

    return executionResults;
  }

  private @NotNull ArrayList<MavenServerExecutionResult> getExecutionResults(MavenSession session,
                                                                             Set<File> files,
                                                                             MavenExecutionRequest request) {
    ArrayList<MavenServerExecutionResult> executionResults = new ArrayList<>();
    try {
      List<ProjectBuildingResult> buildingResults = myTelemetry.callWithSpan("getProjectBuildingResults " + files.size(), () ->
        getProjectBuildingResults(request, files, session));

      List<Exception> exceptions = new ArrayList<>();
      List<MavenProject> projects = new ArrayList<>();
      for (ProjectBuildingResult result : buildingResults) {
        MavenProject project = result.getProject();
        if (project != null) {
          projects.add(project);
        }
      }
      session.setProjects(projects);
      afterProjectsRead(session, exceptions);

      // TODO: Cache does not work actually
      fillSessionCache(session, session.getRepositorySession(), buildingResults);

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

        String newDependencyHash = fileToNewDependencyHash.get(pomFile);
        if (!transitiveDependenciesChanged(pomFile, newDependencyHash, fileToNewDependencyHash)) {
          executionResults.add(createExecutionResult(project, newDependencyHash));
          continue;
        }

        //project.setDependencyArtifacts(project.createArtifacts(myEmbedder.getComponent(ArtifactFactory.class), null, null));

        buildingResultInfos.add(new ProjectBuildingResultInfo(projectId, project, modelProblems, exceptions, newDependencyHash));

        myLongRunningTask.updateTotalRequests(buildingResultInfos.size());
      }

      Collection<MavenServerExecutionResult> execResults =
        myTelemetry.executeWithSpan("resolveBuildingResults",
                                    runInParallel,
                                    buildingResultInfos, br -> {
            if (myLongRunningTask.isCanceled()) return MavenServerExecutionResult.EMPTY;
            MavenServerExecutionResult result = myTelemetry.callWithSpan(
              "resolveBuildingResult " + br.projectId, () ->
                resolveBuildingResult(session.getRepositorySession(), br.mavenProject, br.modelProblems, br.exceptions, br.dependencyHash));
            myLongRunningTask.incrementFinishedRequests();
            return result;
          }
        );

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
    myTelemetry.executeWithSpan("dependencyHashes",
                                runInParallel,
                                buildingResults, br -> {
        String newDependencyHash = Maven40EffectivePomDumper.dependencyHash(br.getProject());
        if (null != newDependencyHash) {
          fileToNewDependencyHash.put(br.getPomFile(), newDependencyHash);
        }
        return br;
      }
    );
    return fileToNewDependencyHash;
  }

  private @NotNull MavenServerExecutionResult resolveBuildingResult(RepositorySystemSession repositorySession,
                                                                    MavenProject project,
                                                                    @NotNull List<ModelProblem> modelProblems,
                                                                    List<Exception> exceptions,
                                                                    String dependencyHash) {
    try {
      DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
      Set<Artifact> artifacts = resolveArtifacts(dependencyResolutionResult);
      project.setArtifacts(artifacts);

      return createExecutionResult(exceptions, modelProblems, project, dependencyResolutionResult, dependencyHash);
    }
    catch (Exception e) {
      return createExecutionResult(project, e);
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
      MavenServerGlobals.getLogger().warn(e);
      resolutionResult = e.getResult();
    }

    Set<Artifact> artifacts = new LinkedHashSet<>();
    if (resolutionResult.getDependencyGraph() != null) {
      try {
        RepositoryUtils.toArtifacts(
          artifacts,
          resolutionResult.getDependencyGraph().getChildren(),
          null == project.getArtifact() ? Collections.emptyList() : Collections.singletonList(project.getArtifact().getId()),
          null);
      }
      catch (Exception e) {

      }


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

  private @NotNull MavenServerExecutionResult createExecutionResult(@NotNull MavenProject mavenProject, String dependencyHash) {
    return createExecutionResult(mavenProject.getFile(), Collections.emptyList(), Collections.emptyList(), mavenProject, null,
                                 dependencyHash, true);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(Exception exception) {
    return createExecutionResult(null, exception);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(@Nullable MavenProject mavenProject, Exception exception) {
    return createExecutionResult(Collections.singletonList(exception), Collections.emptyList(), mavenProject, null, null);
  }

  private @NotNull MavenServerExecutionResult createExecutionResult(List<Exception> exceptions,
                                                                    List<ModelProblem> modelProblems,
                                                                    @Nullable MavenProject mavenProject,
                                                                    DependencyResolutionResult dependencyResolutionResult,
                                                                    String dependencyHash) {
    File file = null == mavenProject ? null : mavenProject.getFile();
    return createExecutionResult(file, exceptions, modelProblems, mavenProject, dependencyResolutionResult, dependencyHash, false);
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

    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<>();
    collectUnresolvedArtifactProblems(file, dependencyResolutionResult, unresolvedProblems);

    if (mavenProject == null) return new MavenServerExecutionResult(file, null, problems, Collections.emptySet());

    MavenModel model = new MavenModel();
    Model nativeModel = mavenProject.getModel();
    Model interpolatedNativeModel = myEmbedder.interpolateAndAlignModel(nativeModel, mavenProject.getBasedir());
    try {
      DependencyNode dependencyGraph =
        dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

      List<DependencyNode> dependencyNodes = dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
      model = Maven40AetherModelConverter.convertModelWithAetherDependencyTree(
        mavenProject,
        interpolatedNativeModel,
        dependencyNodes,
        myLocalRepositoryFile);
    }
    catch (Exception e) {
      problems.addAll(myEmbedder.collectProblems(mavenProject.getFile(), Collections.singleton(e), modelProblems));
    }

    Map<String, List<String>> injectedProfilesMap = mavenProject.getInjectedProfileIds();

    List<String> activatedProfiles = new ArrayList<>();
    for (List<String> profileList : injectedProfilesMap.values()) {
      activatedProfiles.addAll(profileList);
    }

    Map<String, String> mavenModelMap = Maven40ModelConverter.convertToMap(interpolatedNativeModel);
    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, getManagedDependencies(mavenProject), dependencyHash, dependencyResolutionSkipped,
                                                 mavenModelMap, activatedProfiles);
    if (null == model.getBuild() || null == model.getBuild().getDirectory()) {
      data = null;
    }
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


  private void collectUnresolvedArtifactProblems(@Nullable File file,
                                                 @Nullable DependencyResolutionResult result,
                                                 Collection<MavenProjectProblem> problems) {
    if (result == null) return;
    String path = file == null ? "" : file.getPath();
    for (Dependency unresolvedDependency : result.getUnresolvedDependencies()) {
      for (Exception exception : result.getResolutionErrors(unresolvedDependency)) {
        String message = Maven40ServerEmbedderImpl.getRootMessage(exception);
        Artifact artifact = RepositoryUtils.toArtifact(unresolvedDependency.getArtifact());
        MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(artifact, myLocalRepositoryFile);
        problems.add(MavenProjectProblem.createUnresolvedArtifactProblem(path, message, false, mavenArtifact));
        break;
      }
    }
  }

  private @NotNull Set<Artifact> resolveArtifacts(DependencyResolutionResult dependencyResolutionResult) {
    Map<Dependency, Artifact> winnerDependencyMap = new IdentityHashMap<>();
    Set<Artifact> artifacts = new LinkedHashSet<>();
    Set<Dependency> addedDependencies = Collections.newSetFromMap(new IdentityHashMap<>());
    resolveConflicts(dependencyResolutionResult, winnerDependencyMap);

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

    return artifacts;
  }

  private static void fillSessionCache(MavenSession mavenSession,
                                       RepositorySystemSession session,
                                       List<ProjectBuildingResult> buildingResults) {
    int initialCapacity = (int)(buildingResults.size() * 1.5);
    Map<MavenId, org.apache.maven.api.model.Model> cacheMavenModelMap = new HashMap<>(initialCapacity);
    Map<String, MavenProject> mavenProjectMap = new HashMap<>(initialCapacity);
    for (ProjectBuildingResult result : buildingResults) {
      if (result.getProblems() != null && !result.getProblems().isEmpty()) continue;
      org.apache.maven.api.model.Model model = result.getProject().getModel().getDelegate();
      String key = ArtifactUtils.key(model.getGroupId(), model.getArtifactId(), model.getVersion());
      mavenProjectMap.put(key, result.getProject());
      cacheMavenModelMap.put(new MavenId(model.getGroupId(), model.getArtifactId(), model.getVersion()), model);
    }
    mavenSession.setProjectMap(mavenProjectMap);
    Maven40WorkspaceMapReader maven40WorkspaceMapReader = null;
    WorkspaceReader reader = session.getWorkspaceReader();
    if (reader instanceof Maven40WorkspaceMapReader) {
      maven40WorkspaceMapReader = (Maven40WorkspaceMapReader)reader;
    }
    else if (reader instanceof MavenChainedWorkspaceReader) {
      for (WorkspaceReader chainedReader : ((MavenChainedWorkspaceReader)reader).getReaders()) {
        if (chainedReader instanceof Maven40WorkspaceMapReader) {
          maven40WorkspaceMapReader = (Maven40WorkspaceMapReader)chainedReader;
          break;
        }
      }
    }
    if (null != maven40WorkspaceMapReader) {
      maven40WorkspaceMapReader.setCacheModelMap(cacheMavenModelMap);
    }
  }

  /**
   * adapted from {@link DefaultMaven#afterProjectsRead(MavenSession)}
   */
  private void afterProjectsRead(MavenSession session, List<Exception> exceptions) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants =
      myEmbedder.getExtensionComponents(Collections.emptyList(), AbstractMavenLifecycleParticipant.class);
    for (AbstractMavenLifecycleParticipant listener : lifecycleParticipants) {
      Thread.currentThread().setContextClassLoader(listener.getClass().getClassLoader());
      try {
        listener.afterProjectsRead(session);
      }
      catch (Exception e) {
        // Unlike Maven, IDEA sync shouldn't fail even if there is a problem with an extension
        exceptions.add(e);
      }
      finally {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
    }
  }

  private boolean resolveAsModule(Artifact a) {
    MavenWorkspaceMap map = myWorkspaceMap;
    if (map == null) return false;

    MavenWorkspaceMap.Data resolved = map.findFileAndOriginalId(Maven40ModelConverter.createMavenId(a));
    if (resolved == null) return false;

    a.setResolved(true);
    a.setFile(resolved.getFile(a.getType()));
    a.selectVersion(resolved.originalId.getVersion());
    return true;
  }

  private static void resolveConflicts(DependencyResolutionResult dependencyResolutionResult,
                                       Map<Dependency, Artifact> winnerDependencyMap) {
    dependencyResolutionResult.getDependencyGraph().accept(new TreeDependencyVisitor(new DependencyVisitor() {
      @Override
      public boolean visitEnter(DependencyNode node) {
        Object winner = node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        Dependency dependency = node.getDependency();
        if (dependency != null && winner == null) {
          Artifact winnerArtifact = Maven40AetherModelConverter.toArtifact(dependency);
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

  private @NotNull List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Set<File> files,
                                                                         MavenSession session) {
    ProjectBuilder builder = myEmbedder.getComponent(ProjectBuilder.class);

    List<ProjectBuildingResult> buildingResults = new ArrayList<>();

    ProjectBuildingRequest projectBuildingRequest = request.getProjectBuildingRequest();
    projectBuildingRequest.setRepositorySession(session.getRepositorySession());
    projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_STRICT); // to process extensions
    projectBuildingRequest.setActiveProfileIds(request.getActiveProfiles());
    projectBuildingRequest.setInactiveProfileIds(request.getInactiveProfiles());
    projectBuildingRequest.setResolveDependencies(false);

    // org.apache.maven.project.collector.MultiModuleCollectionStrategy.collectProjects
    buildSinglePom(builder, buildingResults, projectBuildingRequest, request.getPom());

    Set<File> processedFiles = new HashSet<>();
    for (ProjectBuildingResult buildingResult : buildingResults) {
      processedFiles.add(buildingResult.getPomFile());
    }
    Set<File> nonProcessedFiles = new HashSet<>(files);
    nonProcessedFiles.removeAll(processedFiles);
    for (File file : nonProcessedFiles) {
      buildSinglePom(builder, buildingResults, projectBuildingRequest, file);
    }

    return buildingResults;
  }

  private static void buildSinglePom(ProjectBuilder builder,
                                     List<ProjectBuildingResult> buildingResults,
                                     ProjectBuildingRequest projectBuildingRequest,
                                     File pomFile) {
    try {
      List<ProjectBuildingResult> build = builder.build(Collections.singletonList(pomFile), true, projectBuildingRequest);
      buildingResults.addAll(build);
    }
    catch (ProjectBuildingException e) {
      Maven40ResolverUtil.handleProjectBuildingException(buildingResults, e);
    }
  }
}
