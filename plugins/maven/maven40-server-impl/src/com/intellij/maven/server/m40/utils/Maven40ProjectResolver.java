// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.*;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
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
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Maven40ProjectResolver {
  @NotNull private final Maven40ServerEmbedderImpl myEmbedder;
  @NotNull private final MavenServerOpenTelemetry myTelemetry;
  private final boolean myUpdateSnapshots;
  @NotNull private final Maven40ImporterSpy myImporterSpy;
  private final LongRunningTask myLongRunningTask;
  private final PomHashMap myPomHashMap;
  private final List<String> myActiveProfiles;
  private final List<String> myInactiveProfiles;
  @Nullable private final MavenWorkspaceMap myWorkspaceMap;
  @NotNull private final File myLocalRepositoryFile;
  @NotNull private final Properties userProperties;
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

  @NotNull
  public ArrayList<MavenServerExecutionResult> resolveProjects() {
    try {
      Collection<Maven40ExecutionResult> results = myTelemetry.callWithSpan("doResolveProject", () -> doResolveProject());
      ArrayList<MavenServerExecutionResult> list = new ArrayList<>();
      results.stream().map(result -> createExecutionResult(result)).forEachOrdered(list::add);
      return list;
    }
    catch (Exception e) {
      throw myEmbedder.wrapToSerializableRuntimeException(e);
    }
  }

  private static class ProjectBuildingResultInfo {
    ProjectBuildingResult buildingResult;
    List<Exception> exceptions;
    String dependencyHash;

    private ProjectBuildingResultInfo(ProjectBuildingResult buildingResult, List<Exception> exceptions, String dependencyHash) {
      this.buildingResult = buildingResult;
      this.exceptions = exceptions;
      this.dependencyHash = dependencyHash;
    }

    @Override
    public String toString() {
      return "ProjectBuildingResultData{" +
             "projectId=" + buildingResult.getProjectId() +
             ", dependencyHash=" + dependencyHash +
             '}';
    }
  }

  @NotNull
  private Collection<Maven40ExecutionResult> doResolveProject() {
    Set<File> files = myPomHashMap.keySet();
    File file = !files.isEmpty() ? files.iterator().next() : null;
    MavenExecutionRequest request = myEmbedder.createRequest(file, myActiveProfiles, myInactiveProfiles, userProperties);

    request.setUpdateSnapshots(myUpdateSnapshots);

    Collection<Maven40ExecutionResult> executionResults = new ArrayList<>();
    List<ProjectBuildingResultInfo> buildingResultInfos = new ArrayList<>();
    myEmbedder.executeWithMavenSession(request, myWorkspaceMap, myLongRunningTask.getIndicator(), session -> {
      try {
        List<ProjectBuildingResult> buildingResults = myTelemetry.callWithSpan("getProjectBuildingResults " + files.size(), () ->
          getProjectBuildingResults(request, files, session));

        // TODO: Cache does not work actually
        //fillSessionCache(session, session.getRepositorySession(), buildingResults);

        boolean runInParallel = myResolveInParallel;
        Map<File, String> fileToNewDependencyHash = new ConcurrentHashMap<>();
        myTelemetry.callWithSpan("dependencyHashes", () ->
          myTelemetry.execute(
            runInParallel,
            buildingResults, br -> {
              String newDependencyHash = Maven40EffectivePomDumper.dependencyHash(br.getProject());
              if (null != newDependencyHash) {
                fileToNewDependencyHash.put(br.getPomFile(), newDependencyHash);
              }
              return br;
            }
          ));

        for (ProjectBuildingResult buildingResult : buildingResults) {
          MavenProject project = buildingResult.getProject();
          File pomFile = buildingResult.getPomFile();

          if (project == null) {
            executionResults.add(new Maven40ExecutionResult(pomFile, buildingResult.getProblems()));
            continue;
          }

          String previousDependencyHash = myPomHashMap.getDependencyHash(buildingResult.getPomFile());
          String newDependencyHash = fileToNewDependencyHash.get(pomFile);
          if (null != previousDependencyHash && previousDependencyHash.equals(newDependencyHash)) {
            Maven40ExecutionResult res = new Maven40ExecutionResult(project, null, new ArrayList<>(), new ArrayList<>());
            res.setDependencyHash(previousDependencyHash);
            res.setDependencyResolutionSkipped(true);
            executionResults.add(res);
            continue;
          }

          List<Exception> exceptions = new ArrayList<>();

          loadExtensions(project, exceptions);

          //project.setDependencyArtifacts(project.createArtifacts(myEmbedder.getComponent(ArtifactFactory.class), null, null));

          buildingResultInfos.add(new ProjectBuildingResultInfo(buildingResult, exceptions, newDependencyHash));

          myLongRunningTask.updateTotalRequests(buildingResultInfos.size());
          Collection<Maven40ExecutionResult> execResults =
            myTelemetry.callWithSpan("resolveBuildingResults", () ->
              myTelemetry.execute(
                runInParallel,
                buildingResultInfos, br -> {
                  if (myLongRunningTask.isCanceled()) return new Maven40ExecutionResult(Collections.emptyList());
                  Maven40ExecutionResult result = myTelemetry.callWithSpan(
                    "resolveBuildingResult " + br.buildingResult.getProjectId(), () ->
                      resolveBuildingResult(session.getRepositorySession(), br.buildingResult, br.exceptions));
                  result.setDependencyHash(br.dependencyHash);
                  myLongRunningTask.incrementFinishedRequests();
                  return result;
                }
              ));

          executionResults.addAll(execResults);
        }
      }
      catch (Exception e) {
        executionResults.add(handleException(e));
      }
    });

    return executionResults;
  }

  @NotNull
  private Maven40ExecutionResult resolveBuildingResult(RepositorySystemSession repositorySession,
                                                       ProjectBuildingResult buildingResult,
                                                       List<Exception> exceptions) {
    MavenProject project = buildingResult.getProject();
    try {
      List<ModelProblem> modelProblems = new ArrayList<>();

      if (buildingResult.getProblems() != null) {
        modelProblems.addAll(buildingResult.getProblems());
      }

      DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
      Set<Artifact> artifacts = resolveArtifacts(dependencyResolutionResult);
      project.setArtifacts(artifacts);

      return new Maven40ExecutionResult(project, dependencyResolutionResult, exceptions, modelProblems);
    }
    catch (Exception e) {
      return handleException(project, e);
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

  @NotNull
  private MavenServerExecutionResult createExecutionResult(Maven40ExecutionResult result) {
    File file = result.getPomFile();
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    myEmbedder.collectProblems(file, result.getExceptions(), result.getModelProblems(), problems);

    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<>();
    collectUnresolvedArtifactProblems(file, result.getDependencyResolutionResult(), unresolvedProblems);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, Collections.emptySet());

    MavenModel model = new MavenModel();
    try {
      DependencyResolutionResult dependencyResolutionResult = result.getDependencyResolutionResult();
      DependencyNode dependencyGraph =
        dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

      List<DependencyNode> dependencyNodes = dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
      model = Maven40AetherModelConverter.convertModelWithAetherDependencyTree(
        mavenProject,
        dependencyNodes,
        myLocalRepositoryFile);
    }
    catch (Exception e) {
      myEmbedder.collectProblems(mavenProject.getFile(), Collections.singleton(e), result.getModelProblems(), problems);
    }

    RemoteNativeMaven40ProjectHolder holder = new RemoteNativeMaven40ProjectHolder(mavenProject);
    try {
      UnicastRemoteObject.exportObject(holder, 0);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    Collection<String> activatedProfiles = Maven40ProfileUtil.collectActivatedProfiles(mavenProject);

    Map<String, String> mavenModelMap = Maven40ModelConverter.convertToMap(mavenProject.getModel());
    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, result.getDependencyHash(), result.isDependencyResolutionSkipped(), mavenModelMap,
                                                 holder, activatedProfiles);
    if (null == model.getBuild() || null == model.getBuild().getDirectory()) {
      data = null;
    }
    return new MavenServerExecutionResult(data, problems, Collections.emptySet(), unresolvedProblems);
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
        problems.add(MavenProjectProblem.createUnresolvedArtifactProblem(path, message, true, mavenArtifact));
        break;
      }
    }
  }

  private static Maven40ExecutionResult handleException(Exception e) {
    return new Maven40ExecutionResult(Collections.singletonList(e));
  }

  private static Maven40ExecutionResult handleException(MavenProject mavenProject, Exception e) {
    return new Maven40ExecutionResult(mavenProject, Collections.singletonList(e));
  }

  @NotNull
  private Set<Artifact> resolveArtifacts(DependencyResolutionResult dependencyResolutionResult) {
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
      ((DefaultRepositorySystemSession)session).setWorkspaceReader(
        new Maven40WorkspaceReader(session.getWorkspaceReader(), cacheMavenModelMap));
    }
  }

  /**
   * adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
   */
  private void loadExtensions(MavenProject project, List<Exception> exceptions) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants =
      myEmbedder.getLifecycleParticipants(Collections.singletonList(project));
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

  @NotNull
  private List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Collection<File> files,
                                                                MavenSession session) {
    ProjectBuilder builder = myEmbedder.getComponent(ProjectBuilder.class);

    List<ProjectBuildingResult> buildingResults = new ArrayList<>();

    ProjectBuildingRequest projectBuildingRequest = request.getProjectBuildingRequest();
    projectBuildingRequest.setRepositorySession(session.getRepositorySession());
    projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    projectBuildingRequest.setActiveProfileIds(request.getActiveProfiles());
    projectBuildingRequest.setInactiveProfileIds(request.getInactiveProfiles());
    projectBuildingRequest.setResolveDependencies(false);

    if (files.size() == 1) {
      buildSinglePom(builder, buildingResults, projectBuildingRequest, files.iterator().next());
    }
    else {
      try {
        buildingResults = builder.build(new ArrayList<>(files), false, projectBuildingRequest);
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
    return buildingResults;
  }

  private static void buildSinglePom(ProjectBuilder builder,
                                     List<ProjectBuildingResult> buildingResults,
                                     ProjectBuildingRequest projectBuildingRequest,
                                     File pomFile) {
    try {
      ProjectBuildingResult build = builder.build(pomFile, projectBuildingRequest);
      buildingResults.add(build);
    }
    catch (ProjectBuildingException e) {
      Maven40ResolverUtil.handleProjectBuildingException(buildingResults, e);
    }
  }
}
