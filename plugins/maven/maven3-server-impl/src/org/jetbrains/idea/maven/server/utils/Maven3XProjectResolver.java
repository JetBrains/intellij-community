// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.apache.maven.project.*;
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.jetbrains.idea.maven.server.MavenServerEmbedder.MAVEN_EMBEDDER_VERSION;

public class Maven3XProjectResolver {
  @NotNull protected final Maven3XServerEmbedder myEmbedder;
  @NotNull private final MavenServerOpenTelemetry myTelemetry;
  private final boolean myUpdateSnapshots;
  @NotNull private final Maven3ImporterSpy myImporterSpy;
  private final LongRunningTask myLongRunningTask;
  private final PomHashMap myPomHashMap;
  private final List<String> myActiveProfiles;
  private final List<String> myInactiveProfiles;
  @Nullable protected final MavenWorkspaceMap myWorkspaceMap;
  @NotNull private final Properties userProperties;
  private final boolean myResolveInParallel;

  public Maven3XProjectResolver(@NotNull Maven3XServerEmbedder embedder,
                                @NotNull MavenServerOpenTelemetry telemetry,
                                boolean updateSnapshots,
                                @NotNull Maven3ImporterSpy importerSpy,
                                @NotNull LongRunningTask longRunningTask,
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
    myPomHashMap = pomHashMap;
    myActiveProfiles = activeProfiles;
    myInactiveProfiles = inactiveProfiles;
    myWorkspaceMap = workspaceMap;
    this.userProperties = userProperties;
    myResolveInParallel = resolveInParallel;
  }

  @NotNull
  public ArrayList<MavenServerExecutionResult> resolveProjects() {
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

  @NotNull
  private ArrayList<MavenServerExecutionResult> doResolveProject() {
    Set<File> files = myPomHashMap.keySet();
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

  @NotNull
  private ArrayList<MavenServerExecutionResult> getExecutionResults(Set<File> files,
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

  @NotNull
  private Map<File, String> collectHashes(boolean runInParallel, List<ProjectBuildingResult> buildingResults) {
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
    if (myWorkspaceMap != null) {
      session.setWorkspaceReader(new Maven3WorkspaceMapReader(myWorkspaceMap));
    }
  }

  @NotNull
  private MavenServerExecutionResult resolveBuildingResult(RepositorySystemSession repositorySession,
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

  @NotNull
  private MavenServerExecutionResult createExecutionResult(@NotNull MavenProject mavenProject, String dependencyHash) {
    return createExecutionResult(mavenProject.getFile(), Collections.emptyList(), Collections.emptyList(), mavenProject, null,
                                 dependencyHash, true);
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(Exception exception) {
    return createExecutionResult(null, exception);
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(MavenProject mavenProject, Exception exception) {
    return createExecutionResult(Collections.singletonList(exception), Collections.emptyList(), mavenProject, null, null);
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(List<Exception> exceptions,
                                                           List<ModelProblem> modelProblems,
                                                           MavenProject mavenProject,
                                                           DependencyResolutionResult dependencyResolutionResult,
                                                           String dependencyHash) {
    return createExecutionResult(null, exceptions, modelProblems, mavenProject, dependencyResolutionResult, dependencyHash, false);
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(@Nullable File file, List<ModelProblem> modelProblems) {
    return createExecutionResult(file, Collections.emptyList(), modelProblems, null, null, null, false);
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(@Nullable File file,
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

    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, Collections.emptySet());

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
    return new MavenServerExecutionResult(data, problems, Collections.emptySet(), unresolvedProblems);
  }

  @NotNull
  private static List<MavenId> getManagedDependencies(@Nullable MavenProject project) {

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
      ((DefaultRepositorySystemSession)session).setWorkspaceReader(
        new Maven3WorkspaceReader(session.getWorkspaceReader(), cacheMavenModelMap));
    }
  }

  @NotNull
  protected List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Collection<File> files) {
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
      if (files.size() == 1) {
        buildSinglePom(builder, buildingResults, projectBuildingRequest, files.iterator().next());
      }
      else {
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
    buildingResults.addAll(builder.build(new ArrayList<>(files), false, projectBuildingRequest));
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

  @NotNull
  private Set<Artifact> resolveArtifacts(DependencyResolutionResult dependencyResolutionResult, boolean addUnresolvedNodes) {
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
        problems.add(MavenProjectProblem.createUnresolvedArtifactProblem(path, message, true, mavenArtifact));
        break;
      }
    }
  }

  @NotNull
  private static String getRootMessage(Throwable each) {
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
