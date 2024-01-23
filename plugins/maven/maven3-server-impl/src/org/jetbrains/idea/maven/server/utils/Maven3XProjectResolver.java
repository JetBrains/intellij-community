// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.*;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
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
import org.jetbrains.idea.maven.server.embedder.Maven3ExecutionResult;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import static org.jetbrains.idea.maven.server.Maven3ServerEmbedder.USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING;
import static org.jetbrains.idea.maven.server.MavenServerEmbedder.MAVEN_EMBEDDER_VERSION;

public class Maven3XProjectResolver {
  @NotNull private final Maven3XServerEmbedder myEmbedder;
  private final boolean myUpdateSnapshots;
  @NotNull private final Maven3ImporterSpy myImporterSpy;
  @NotNull private final MavenServerConsoleIndicatorImpl myCurrentIndicator;
  @Nullable private final MavenWorkspaceMap myWorkspaceMap;
  @NotNull private final Maven3ServerConsoleLogger myConsoleWrapper;
  @NotNull private final ArtifactRepository myLocalRepository;
  @NotNull private final Properties userProperties;
  private final boolean myResolveInParallel;

  public Maven3XProjectResolver(@NotNull Maven3XServerEmbedder embedder,
                                boolean updateSnapshots,
                                @NotNull Maven3ImporterSpy importerSpy,
                                @NotNull MavenServerConsoleIndicatorImpl currentIndicator,
                                @Nullable MavenWorkspaceMap workspaceMap,
                                @NotNull Maven3ServerConsoleLogger consoleWrapper,
                                @NotNull ArtifactRepository localRepository,
                                @NotNull Properties userProperties,
                                boolean resolveInParallel) {
    myEmbedder = embedder;
    myUpdateSnapshots = updateSnapshots;
    myImporterSpy = importerSpy;
    myCurrentIndicator = currentIndicator;
    myWorkspaceMap = workspaceMap;
    myConsoleWrapper = consoleWrapper;
    myLocalRepository = localRepository;
    this.userProperties = userProperties;
    myResolveInParallel = resolveInParallel;
  }

  @NotNull
  public ArrayList<MavenServerExecutionResult> resolveProjects(@NotNull LongRunningTask task,
                                                               @NotNull Map<File, String> fileToChecksum,
                                                               @NotNull List<String> activeProfiles,
                                                               @NotNull List<String> inactiveProfiles) {
    try {
      DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);

      Collection<Maven3ExecutionResult> results = doResolveProject(
        task,
        fileToChecksum,
        activeProfiles,
        inactiveProfiles,
        Collections.singletonList(listener)
      );

      ArrayList<MavenServerExecutionResult> list = new ArrayList<>();
      results.stream().map(result -> createExecutionResult(result.getPomFile(), result, listener.getRootNode()))
        .forEachOrdered(list::add);
      return list;
    }
    catch (Exception e) {
      throw myEmbedder.wrapToSerializableRuntimeException(e);
    }
  }

  private static class ProjectBuildingResultInfo {
    ProjectBuildingResult buildingResult;
    List<Exception> exceptions;
    String checksum;

    private ProjectBuildingResultInfo(ProjectBuildingResult buildingResult, List<Exception> exceptions, String checksum) {
      this.buildingResult = buildingResult;
      this.exceptions = exceptions;
      this.checksum = checksum;
    }

    @Override
    public String toString() {
      return "ProjectBuildingResultData{" +
             "projectId=" + buildingResult.getProjectId() +
             ", checksum=" + checksum +
             '}';
    }
  }

  @NotNull
  private Collection<Maven3ExecutionResult> doResolveProject(@NotNull LongRunningTask task,
                                                             @NotNull Map<File, String> fileToChecksum,
                                                             @NotNull List<String> activeProfiles,
                                                             @NotNull List<String> inactiveProfiles,
                                                             List<ResolutionListener> listeners) {
    Set<File> files = fileToChecksum.keySet();
    File file = !files.isEmpty() ? files.iterator().next() : null;
    files.forEach(f -> MavenServerStatsCollector.fileRead(f));
    MavenExecutionRequest request = myEmbedder.createRequest(file, activeProfiles, inactiveProfiles, userProperties);

    request.setUpdateSnapshots(myUpdateSnapshots);

    Collection<Maven3ExecutionResult> executionResults = new ArrayList<>();
    List<ProjectBuildingResultInfo> buildingResultInfos = new ArrayList<>();

    myEmbedder.executeWithMavenSession(request, () -> {
      try {
        MavenSession mavenSession = myEmbedder.getComponent(LegacySupport.class).getSession();
        RepositorySystemSession repositorySession = myEmbedder.getComponent(LegacySupport.class).getRepositorySession();
        if (repositorySession instanceof DefaultRepositorySystemSession) {
          DefaultRepositorySystemSession session = (DefaultRepositorySystemSession)repositorySession;
          myImporterSpy.setIndicator(myCurrentIndicator);
          session.setTransferListener(new Maven3TransferListenerAdapter(myCurrentIndicator));

          if (myWorkspaceMap != null) {
            session.setWorkspaceReader(new Maven3WorkspaceMapReader(myWorkspaceMap));
          }

          session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
          session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
        }

        List<ProjectBuildingResult> buildingResults = getProjectBuildingResults(request, files);

        fillSessionCache(mavenSession, repositorySession, buildingResults);

        boolean addUnresolved = System.getProperty("idea.maven.no.use.dependency.graph") == null;

        for (ProjectBuildingResult buildingResult : buildingResults) {
          MavenProject project = buildingResult.getProject();

          if (project == null) {
            List<Exception> exceptions = new ArrayList<>();
            for (ModelProblem problem : buildingResult.getProblems()) {
              exceptions.add(problem.getException());
            }
            executionResults.add(new Maven3ExecutionResult(buildingResult.getPomFile(), exceptions));
            continue;
          }

          String previousChecksum = fileToChecksum.get(buildingResult.getPomFile());
          String newChecksum = Maven3EffectivePomDumper.checksum(buildingResult.getProject());
          if (null != previousChecksum && previousChecksum.equals(newChecksum)) {
            continue;
          }

          List<Exception> exceptions = new ArrayList<>();

          loadExtensions(project, exceptions);

          project.setDependencyArtifacts(project.createArtifacts(myEmbedder.getComponent(ArtifactFactory.class), null, null));

          if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING) {
            executionResults.add(resolveMvn2CompatResult(project, exceptions, listeners, myLocalRepository));
          }
          else {
            buildingResultInfos.add(new ProjectBuildingResultInfo(buildingResult, exceptions, newChecksum));
          }
        }

        task.updateTotalRequests(buildingResultInfos.size());
        boolean runInParallel = myResolveInParallel;
        Collection<Maven3ExecutionResult> execResults =
          ParallelRunnerForServer.execute(
            runInParallel,
            buildingResultInfos, br -> {
              if (task.isCanceled()) return new Maven3ExecutionResult(Collections.emptyList());
              Maven3ExecutionResult result = resolveBuildingResult(repositorySession, addUnresolved, br.buildingResult, br.exceptions);
              result.setChecksum(br.checksum);
              task.incrementFinishedRequests();
              return result;
            }
          );

        executionResults.addAll(execResults);
      }
      catch (Exception e) {
        executionResults.add(handleException(e));
      }
    });

    return executionResults;
  }

  @NotNull
  private Maven3ExecutionResult resolveBuildingResult(RepositorySystemSession repositorySession,
                                                      boolean addUnresolved,
                                                      ProjectBuildingResult buildingResult,
                                                      List<Exception> exceptions) {
    MavenProject project = buildingResult.getProject();
    try {
      List<ModelProblem> modelProblems = new ArrayList<>();

      if (buildingResult.getProblems() != null) {
        modelProblems.addAll(buildingResult.getProblems());
      }

      DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
      Set<Artifact> artifacts = resolveArtifacts(dependencyResolutionResult, addUnresolved);
      project.setArtifacts(artifacts);

      return new Maven3ExecutionResult(project, dependencyResolutionResult, exceptions, modelProblems);
    }
    catch (Exception e) {
      return handleException(project, e);
    }
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(@Nullable File file, Maven3ExecutionResult result, DependencyNode rootNode) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    myEmbedder.collectProblems(file, result.getExceptions(), result.getModelProblems(), problems);

    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<>();
    collectUnresolvedArtifactProblems(file, result.getDependencyResolutionResult(), unresolvedProblems);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, Collections.emptySet());

    MavenModel model = new MavenModel();
    try {
      if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING) {
        //noinspection unchecked
        List<DependencyNode> dependencyNodes = rootNode == null ? Collections.emptyList() : rootNode.getChildren();
        model = Maven3ModelConverter.convertModel(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), myEmbedder.getLocalRepositoryFile());
      }
      else {
        DependencyResolutionResult dependencyResolutionResult = result.getDependencyResolutionResult();
        org.eclipse.aether.graph.DependencyNode dependencyGraph =
          dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

        List<org.eclipse.aether.graph.DependencyNode> dependencyNodes =
          dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
        model = Maven3AetherModelConverter.convertModelWithAetherDependencyTree(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), myEmbedder.getLocalRepositoryFile());
      }
    }
    catch (Exception e) {
      myEmbedder.collectProblems(mavenProject.getFile(), Collections.singleton(e), result.getModelProblems(), problems);
    }

    RemoteNativeMaven3ProjectHolder holder = new RemoteNativeMaven3ProjectHolder(mavenProject);
    try {
      UnicastRemoteObject.exportObject(holder, 0);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    Collection<String> activatedProfiles = Maven3XProfileUtil.collectActivatedProfiles(mavenProject);

    Map<String, String> mavenModelMap = Maven3ModelConverter.convertToMap(mavenProject.getModel());
    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, result.getChecksum(), mavenModelMap, holder, activatedProfiles);
    return new MavenServerExecutionResult(data, problems, Collections.emptySet(), unresolvedProblems);
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
  private List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Collection<File> files) {
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
    }
    finally {
      if (modelInterpolator instanceof CustomMaven3ModelInterpolator2 && savedLocalRepository != null) {
        ((CustomMaven3ModelInterpolator2)modelInterpolator).setLocalRepository(savedLocalRepository);
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

  private Maven3ExecutionResult resolveMvn2CompatResult(MavenProject project,
                                                        List<Exception> exceptions,
                                                        List<ResolutionListener> listeners,
                                                        ArtifactRepository localRepository) {
    ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest();
    resolutionRequest.setArtifactDependencies(project.getDependencyArtifacts());
    resolutionRequest.setArtifact(project.getArtifact());
    resolutionRequest.setManagedVersionMap(project.getManagedVersionMap());
    resolutionRequest.setLocalRepository(localRepository);
    resolutionRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
    resolutionRequest.setListeners(listeners);

    resolutionRequest.setResolveRoot(false);
    resolutionRequest.setResolveTransitively(true);

    ArtifactResolver resolver = myEmbedder.getComponent(ArtifactResolver.class);
    ArtifactResolutionResult result = resolver.resolve(resolutionRequest);

    project.setArtifacts(result.getArtifacts());
    return new Maven3ExecutionResult(project, exceptions);
  }

  private static Maven3ExecutionResult handleException(Exception e) {
    return new Maven3ExecutionResult(Collections.singletonList(e));
  }

  private static Maven3ExecutionResult handleException(MavenProject mavenProject, Exception e) {
    return new Maven3ExecutionResult(mavenProject, Collections.singletonList(e));
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
      Queue<org.eclipse.aether.graph.DependencyNode> queue =
        new ArrayDeque<>(dependencyResolutionResult.getDependencyGraph().getChildren());
      while (!queue.isEmpty()) {
        org.eclipse.aether.graph.DependencyNode node = queue.poll();
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
      public boolean visitEnter(org.eclipse.aether.graph.DependencyNode node) {
        Object winner = node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        Dependency dependency = node.getDependency();
        if (dependency != null && winner == null) {
          Artifact winnerArtifact = Maven3AetherModelConverter.toArtifact(dependency);
          winnerDependencyMap.put(dependency, winnerArtifact);
        }
        return true;
      }

      @Override
      public boolean visitLeave(org.eclipse.aether.graph.DependencyNode node) {
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
