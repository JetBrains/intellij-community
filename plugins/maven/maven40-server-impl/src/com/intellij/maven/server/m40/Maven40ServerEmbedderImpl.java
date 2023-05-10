// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import com.intellij.maven.server.m40.utils.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtilRt;
import com.intellij.util.containers.ContainerUtilRt;
import org.apache.commons.cli.ParseException;
import org.apache.maven.*;
import org.apache.maven.api.ArtifactCoordinate;
import org.apache.maven.api.DependencyCoordinate;
import org.apache.maven.api.Node;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ArtifactResolver;
import org.apache.maven.api.services.ArtifactResolverResult;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.execution.*;
import org.apache.maven.internal.impl.DefaultSession;
import org.apache.maven.internal.impl.DefaultSessionFactory;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.*;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.project.*;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.session.scope.internal.SessionScope;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.SettingsBuilder;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.BaseLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.intellij.maven.server.m40.utils.Maven40ModelConverter.convertRemoteRepositories;

public class Maven40ServerEmbedderImpl extends MavenServerEmbeddedBase {
  @NotNull private final DefaultPlexusContainer myContainer;
  @NotNull private final Settings myMavenSettings;

  private final Maven40ServerConsoleLogger myConsoleWrapper;

  private final Properties mySystemProperties;

  private volatile MavenServerProgressIndicatorWrapper myCurrentIndicator;

  private MavenWorkspaceMap myWorkspaceMap;

  private boolean myAlwaysUpdateSnapshots;

  @NotNull private final RepositorySystem myRepositorySystem;

  @NotNull private final Maven40ImporterSpy myImporterSpy;

  private final AtomicReference<ProjectDependenciesResolver> myDependenciesResolver = new AtomicReference<>();

  private final AtomicReference<PluginDependenciesResolver> myPluginDependenciesResolver = new AtomicReference<>();

  @NotNull protected final MavenEmbedderSettings myEmbedderSettings;

  public Maven40ServerEmbedderImpl(MavenEmbedderSettings settings) {
    myEmbedderSettings = settings;

    String multiModuleProjectDirectory = settings.getMultiModuleProjectDirectory();
    if (multiModuleProjectDirectory != null) {
      System.setProperty("user.dir", multiModuleProjectDirectory);
      System.setProperty("maven.multiModuleProjectDirectory", multiModuleProjectDirectory);
    }
    else {
      // initialize maven.multiModuleProjectDirectory property to avoid failure in org.apache.maven.cli.MavenCli#initialize method
      System.setProperty("maven.multiModuleProjectDirectory", "");
    }

    MavenServerSettings serverSettings = settings.getSettings();
    String mavenHome = serverSettings.getMavenHomePath();
    if (mavenHome != null) {
      System.setProperty("maven.home", mavenHome);
    }

    myConsoleWrapper = new Maven40ServerConsoleLogger();
    myConsoleWrapper.setThreshold(serverSettings.getLoggingLevel());

    ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    MavenCli cli = new MavenCli(classWorld) {
      @Override
      protected void customizeContainer(PlexusContainer container) {
        ((DefaultPlexusContainer)container).setLoggerManager(new BaseLoggerManager() {
          @Override
          protected Logger createLogger(String s) {
            return myConsoleWrapper;
          }
        });
      }
    };

    SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
    Class<?> cliRequestClass;
    try {
      cliRequestClass = MavenCli.class.getClassLoader().loadClass("org.apache.maven.cli.CliRequest");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("unable to find maven CliRequest class");
    }

    Object cliRequest;
    try {
      List<String> commandLineOptions = new ArrayList<>(serverSettings.getUserProperties().size());
      for (Map.Entry<Object, Object> each : serverSettings.getUserProperties().entrySet()) {
        commandLineOptions.add("-D" + each.getKey() + "=" + each.getValue());
      }

      if (serverSettings.getLoggingLevel() == MavenServerConsole.LEVEL_DEBUG) {
        commandLineOptions.add("-X");
        commandLineOptions.add("-e");
      }
      else if (serverSettings.getLoggingLevel() == MavenServerConsole.LEVEL_DISABLED) {
        commandLineOptions.add("-q");
      }

      String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
      if (mavenEmbedderCliOptions != null) {
        commandLineOptions.addAll(StringUtilRt.splitHonorQuotes(mavenEmbedderCliOptions, ' '));
      }
      if (commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots")) {
        myAlwaysUpdateSnapshots = true;
      }

      Constructor<?> constructor = cliRequestClass.getDeclaredConstructor(String[].class, ClassWorld.class);
      constructor.setAccessible(true);
      //noinspection SSBasedInspection
      cliRequest = constructor.newInstance(commandLineOptions.toArray(new String[0]), classWorld);

      for (String each : new String[]{"initialize", "cli", "logging", "properties"}) {
        Method m = MavenCli.class.getDeclaredMethod(each, cliRequestClass);
        m.setAccessible(true);
        m.invoke(cli, cliRequest);
      }
    }
    catch (Exception e) {
      ParseException cause = ExceptionUtilRt.findCause(e, ParseException.class);
      if (cause != null) {
        String workingDir = settings.getMultiModuleProjectDirectory();
        if (workingDir == null) {
          workingDir = System.getProperty("user.dir");
        }
        throw new MavenConfigParseException(cause.getMessage(), workingDir);
      }
      throw new RuntimeException(e);
    }

    // reset threshold
    try {
      Method m = MavenCli.class.getDeclaredMethod("container", cliRequestClass);
      m.setAccessible(true);
      myContainer = (DefaultPlexusContainer)m.invoke(cli, cliRequest);
    }
    catch (Exception e) {
      if (e instanceof InvocationTargetException) {
        if (((InvocationTargetException)e).getTargetException().getClass().getCanonicalName()
          .equals("org.apache.maven.cli.internal.ExtensionResolutionException")) {
          MavenId id = extractIdFromException(((InvocationTargetException)e).getTargetException());
          throw new MavenCoreInitializationException(
            wrapToSerializableRuntimeException(((InvocationTargetException)e).getTargetException()), id);
        }
      }
      throw wrapToSerializableRuntimeException(e);
    }

    myContainer.getLoggerManager().setThreshold(serverSettings.getLoggingLevel());

    mySystemProperties = ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "systemProperties");

    if (serverSettings.getProjectJdk() != null) {
      mySystemProperties.setProperty("java.home", serverSettings.getProjectJdk());
    }

    myMavenSettings = Maven40SettingsBuilder.buildSettings(
      settingsBuilder,
      serverSettings,
      mySystemProperties,
      ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "userProperties")
    );

    myRepositorySystem = getComponent(RepositorySystem.class);

    Maven40ImporterSpy importerSpy = getComponentIfExists(Maven40ImporterSpy.class);

    if (importerSpy == null) {
      importerSpy = new Maven40ImporterSpy();
      myContainer.addComponent(importerSpy, Maven40ImporterSpy.class.getName());
    }
    myImporterSpy = importerSpy;
  }

  @NotNull
  @Override
  public Collection<MavenServerExecutionResult> resolveProjects(@NotNull String longRunningTaskId,
                                                                @NotNull Collection<File> files,
                                                                @NotNull Collection<String> activeProfiles,
                                                                @NotNull Collection<String> inactiveProfiles, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = new LongRunningTask(longRunningTaskId, files.size())) {
      return resolveProjects(task, files, activeProfiles, inactiveProfiles);
    }
  }

  @NotNull
  private Collection<MavenServerExecutionResult> resolveProjects(@NotNull LongRunningTask task,
                                                                 @NotNull Collection<File> files,
                                                                 @NotNull Collection<String> activeProfiles,
                                                                 @NotNull Collection<String> inactiveProfiles) {
    try {
      Collection<Maven40ExecutionResult> results = doResolveProject(
        task,
        files,
        new ArrayList<>(activeProfiles),
        new ArrayList<>(inactiveProfiles)
      );

      return ContainerUtilRt.map2List(results, result -> createExecutionResult(result));
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  private Collection<Maven40ExecutionResult> doResolveProject(@NotNull LongRunningTask task,
                                                              @NotNull Collection<File> files,
                                                              @NotNull List<String> activeProfiles,
                                                              @NotNull List<String> inactiveProfiles) {
    File file = !files.isEmpty() ? files.iterator().next() : null;
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles);

    request.setUpdateSnapshots(myAlwaysUpdateSnapshots);

    Collection<Maven40ExecutionResult> executionResults = new ArrayList<>();
    Map<ProjectBuildingResult, List<Exception>> buildingResultsToResolveDependencies = new HashMap<>();

    executeWithMavenSession(request, () -> {
      try {
        MavenSession mavenSession = getComponent(LegacySupport.class).getSession();
        RepositorySystemSession repositorySession = getComponent(LegacySupport.class).getRepositorySession();
        if (repositorySession instanceof DefaultRepositorySystemSession) {
          DefaultRepositorySystemSession session = (DefaultRepositorySystemSession)repositorySession;
          myImporterSpy.setIndicator(myCurrentIndicator);
          session.setTransferListener(new Maven40TransferListenerAdapter(myCurrentIndicator));

          if (myWorkspaceMap != null) {
            session.setWorkspaceReader(new Maven40WorkspaceMapReader(myWorkspaceMap));
          }

          session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
          session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
        }

        List<ProjectBuildingResult> buildingResults = getProjectBuildingResults(request, files);
        fillSessionCache(mavenSession, repositorySession, buildingResults);

        for (ProjectBuildingResult buildingResult : buildingResults) {
          MavenProject project = buildingResult.getProject();

          if (project == null) {
            List<Exception> exceptions = new ArrayList<>();
            for (ModelProblem problem : buildingResult.getProblems()) {
              exceptions.add(problem.getException());
            }
            executionResults.add(new Maven40ExecutionResult(buildingResult.getPomFile(), exceptions));
            continue;
          }

          List<Exception> exceptions = new ArrayList<>();

          loadExtensions(project, exceptions);

          project.setDependencyArtifacts(project.createArtifacts(getComponent(ArtifactFactory.class), null, null));

          buildingResultsToResolveDependencies.put(buildingResult, exceptions);
        }

        task.updateTotalRequests(buildingResultsToResolveDependencies.size());
        boolean runInParallel = canResolveDependenciesInParallel();
        Collection<Maven40ExecutionResult> execResults =
          MavenServerParallelRunner.execute(
            runInParallel,
            buildingResultsToResolveDependencies.entrySet(), entry -> {
              if (task.isCanceled()) return new Maven40ExecutionResult(Collections.emptyList());
              Maven40ExecutionResult result = resolveBuildingResult(repositorySession, entry.getKey(), entry.getValue());
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

  private boolean canResolveDependenciesInParallel() {
    return true;
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

  @NotNull
  private MavenServerExecutionResult createExecutionResult(Maven40ExecutionResult result) {
    File file = result.getPomFile();
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    collectProblems(file, result.getExceptions(), result.getModelProblems(), problems);

    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<>();
    collectUnresolvedArtifactProblems(file, result.getDependencyResolutionResult(), unresolvedProblems);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, Collections.emptySet());

    MavenModel model = new MavenModel();
    try {
      DependencyResolutionResult dependencyResolutionResult = result.getDependencyResolutionResult();
      DependencyNode dependencyGraph =
        dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

      List<DependencyNode> dependencyNodes =
        dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
      model = Maven40AetherModelConverter.convertModelWithAetherDependencyTree(
        mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
        mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());
    }
    catch (Exception e) {
      collectProblems(mavenProject.getFile(), Collections.singleton(e), result.getModelProblems(), problems);
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
      new MavenServerExecutionResult.ProjectData(model, mavenModelMap, holder, activatedProfiles);
    if (null == model.getBuild() || null == model.getBuild().getDirectory()) {
      data = null;
    }
    return new MavenServerExecutionResult(data, problems, Collections.emptySet(), unresolvedProblems);
  }

  private File getLocalRepositoryFile() {
    return new File(myEmbedderSettings.getSettings().getLocalRepositoryPath());
  }

  private void collectProblems(@Nullable File file,
                               @NotNull Collection<? extends Exception> exceptions,
                               @NotNull List<? extends ModelProblem> modelProblems,
                               @NotNull Collection<? super MavenProjectProblem> collector) {
    for (Throwable each : exceptions) {
      collector.addAll(collectExceptionProblems(file, each));
    }
    for (ModelProblem problem : modelProblems) {
      String source;
      if (!StringUtilRt.isEmptyOrSpaces(problem.getSource())) {
        source = problem.getSource() +
                 ":" +
                 problem.getLineNumber() +
                 ":" +
                 problem.getColumnNumber();
      }
      else {
        source = file == null ? "" : file.getPath();
      }
      myConsoleWrapper.error("Maven model problem: " +
                             problem.getMessage() +
                             " at " +
                             problem.getSource() +
                             ":" +
                             problem.getLineNumber() +
                             ":" +
                             problem.getColumnNumber());
      Exception problemException = problem.getException();
      if (problemException != null) {
        List<MavenProjectProblem> exceptionProblems = collectExceptionProblems(file, problemException);
        if (exceptionProblems.isEmpty()) {
          myConsoleWrapper.error("Maven model problem", problemException);
          collector.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage()));
        }
        else {
          collector.addAll(exceptionProblems);
        }
      }
      else {
        collector.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage(), true));
      }
    }
  }

  private List<MavenProjectProblem> collectExceptionProblems(@Nullable File file, Throwable ex) {
    List<MavenProjectProblem> result = new ArrayList<>();
    if (ex == null) return result;

    MavenServerGlobals.getLogger().print(ExceptionUtils.getFullStackTrace(ex));
    myConsoleWrapper.info("Validation error:", ex);

    Artifact problemTransferArtifact = getProblemTransferArtifact(ex);
    if (ex instanceof IllegalStateException && ex.getCause() != null) {
      ex = ex.getCause();
    }

    String path = file == null ? "" : file.getPath();
    if (path.isEmpty() && ex instanceof ProjectBuildingException) {
      File pomFile = ((ProjectBuildingException)ex).getPomFile();
      path = pomFile == null ? "" : pomFile.getPath();
    }

    if (ex instanceof ProjectBuildingException) {
      String causeMessage = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
      result.add(MavenProjectProblem.createStructureProblem(path, causeMessage));
    }
    else if (ex.getStackTrace().length > 0 && ex.getClass().getPackage().getName().equals("groovy.lang")) {
      myConsoleWrapper.error("Maven server structure problem", ex);
      StackTraceElement traceElement = ex.getStackTrace()[0];
      result.add(MavenProjectProblem.createStructureProblem(
        traceElement.getFileName() + ":" + traceElement.getLineNumber(), ex.getMessage()));
    }
    else if (problemTransferArtifact != null) {
      myConsoleWrapper.error("[server] Maven transfer artifact problem: " + problemTransferArtifact);
      String message = getRootMessage(ex);
      MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(problemTransferArtifact, getLocalRepositoryFile());
      result.add(MavenProjectProblem.createRepositoryProblem(path, message, true, mavenArtifact));
    }
    else {
      myConsoleWrapper.error("Maven server structure problem", ex);
      result.add(MavenProjectProblem.createStructureProblem(path, getRootMessage(ex), true));
    }
    return result;
  }

  @NotNull
  private static String getRootMessage(Throwable each) {
    String baseMessage = each.getMessage() != null ? each.getMessage() : "";
    Throwable rootCause = ExceptionUtils.getRootCause(each);
    String rootMessage = rootCause != null ? rootCause.getMessage() : "";
    return StringUtils.isNotEmpty(rootMessage) ? rootMessage : baseMessage;
  }

  @Nullable
  private static Artifact getProblemTransferArtifact(Throwable each) {
    Throwable[] throwables = ExceptionUtils.getThrowables(each);
    if (throwables == null) return null;
    for (Throwable throwable : throwables) {
      if (throwable instanceof ArtifactTransferException) {
        return RepositoryUtils.toArtifact(((ArtifactTransferException)throwable).getArtifact());
      }
    }
    return null;
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
        MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(artifact, getLocalRepositoryFile());
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

  /**
   * copied from {@link DefaultProjectBuilder#resolveDependencies(MavenProject, RepositorySystemSession)}
   */
  private DependencyResolutionResult resolveDependencies(MavenProject project, RepositorySystemSession session) {
    DependencyResolutionResult resolutionResult;

    try {
      ProjectDependenciesResolver dependencyResolver = getDependenciesResolver();
      DefaultDependencyResolutionRequest resolution = new DefaultDependencyResolutionRequest(project, session);
      resolutionResult = dependencyResolver.resolve(resolution);
    }
    catch (DependencyResolutionException e) {
      resolutionResult = e.getResult();
    }

    Set<Artifact> artifacts = new LinkedHashSet<>();
    if (resolutionResult.getDependencyGraph() != null) {
      RepositoryUtils.toArtifacts(
        artifacts,
        resolutionResult.getDependencyGraph().getChildren(),
        null == project.getArtifact() ? Collections.emptyList() : Collections.singletonList(project.getArtifact().getId()),
        null);

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
  private ProjectDependenciesResolver getDependenciesResolver() {
    ProjectDependenciesResolver dependenciesResolver = myDependenciesResolver.get();
    if (dependenciesResolver != null) return dependenciesResolver;
    return myDependenciesResolver.updateAndGet(value -> value == null ? createDependenciesResolver() : value);
  }

  // TODO: useCustomDependenciesResolver
  @NotNull
  protected ProjectDependenciesResolver createDependenciesResolver() {
    return getComponent(ProjectDependenciesResolver.class);
  }

  @SuppressWarnings({"unchecked"})
  private <T> T getComponent(Class<T> clazz, String roleHint) {
    try {
      return (T)myContainer.lookup(clazz.getName(), roleHint);
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz) {
    try {
      return (T)myContainer.lookup(clazz.getName());
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  private <T> T getComponentIfExists(Class<T> clazz) {
    try {
      return (T)myContainer.lookup(clazz.getName());
    }
    catch (ComponentLookupException e) {
      return null;
    }
  }

  private <T> T getComponentIfExists(Class<T> clazz, String roleHint) {
    try {
      return (T)myContainer.lookup(clazz.getName(), roleHint);
    }
    catch (ComponentLookupException e) {
      return null;
    }
  }

  private MavenId extractIdFromException(Throwable exception) {
    try {
      Field field = exception.getClass().getDeclaredField("extension");
      field.setAccessible(true);
      CoreExtension extension = (CoreExtension)field.get(exception);
      return new MavenId(extension.getGroupId(), extension.getArtifactId(), extension.getVersion());
    }
    catch (Throwable e) {
      return null;
    }
  }

  public MavenExecutionRequest createRequest(@Nullable File file,
                                             @Nullable List<String> activeProfiles,
                                             @Nullable List<String> inactiveProfiles) {

    MavenExecutionRequest result = new DefaultMavenExecutionRequest();

    try {
      getComponent(MavenExecutionRequestPopulator.class).populateFromSettings(result, myMavenSettings);

      result.setPom(file);

      getComponent(MavenExecutionRequestPopulator.class).populateDefaults(result);

      result.setSystemProperties(mySystemProperties);
      Properties userProperties = new Properties();
      if (file != null) {
        userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigProperties(file.getParentFile()));
      }
      result.setUserProperties(userProperties);

      result.setActiveProfiles(collectActiveProfiles(result.getActiveProfiles(), activeProfiles, inactiveProfiles));
      if (inactiveProfiles != null) {
        result.setInactiveProfiles(inactiveProfiles);
      }
      result.setCacheNotFound(true);
      result.setCacheTransferError(true);

      result.setStartTime(new Date());

      File mavenMultiModuleProjectDirectory = getMultimoduleProjectDir(file);
      result.setBaseDirectory(mavenMultiModuleProjectDirectory);

      result.setMultiModuleProjectDirectory(mavenMultiModuleProjectDirectory);

      return result;
    }
    catch (MavenExecutionRequestPopulationException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static File getMultimoduleProjectDir(@Nullable File file) {
    File mavenMultiModuleProjectDirectory;
    if (file == null) {
      mavenMultiModuleProjectDirectory = new File(FileUtilRt.getTempDirectory());
    }
    else {
      mavenMultiModuleProjectDirectory = MavenServerUtil.findMavenBasedir(file);
    }
    return mavenMultiModuleProjectDirectory;
  }

  private static List<String> collectActiveProfiles(@Nullable List<String> defaultActiveProfiles,
                                                    @Nullable List<String> explicitActiveProfiles,
                                                    @Nullable List<String> explicitInactiveProfiles) {
    if (defaultActiveProfiles == null || defaultActiveProfiles.isEmpty()) {
      return explicitActiveProfiles != null ? explicitActiveProfiles : Collections.emptyList();
    }

    Set<String> result = new HashSet<>(defaultActiveProfiles);
    if (explicitInactiveProfiles != null && !explicitInactiveProfiles.isEmpty()) {
      result.removeAll(explicitInactiveProfiles);
    }

    if (explicitActiveProfiles != null) {
      result.addAll(explicitActiveProfiles);
    }

    return new ArrayList<>(result);
  }

  public void executeWithMavenSession(MavenExecutionRequest request, Runnable runnable) {
    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    SessionScope sessionScope = getComponent(SessionScope.class);
    sessionScope.enter();

    try {
      MavenSession mavenSession = createMavenSession(request, maven);
      sessionScope.seed(MavenSession.class, mavenSession);
      LegacySupport legacySupport = getComponent(LegacySupport.class);
      MavenSession oldSession = legacySupport.getSession();
      legacySupport.setSession(mavenSession);

      notifyAfterSessionStart(mavenSession);
      // adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
      try {
        runnable.run();
      }
      finally {
        legacySupport.setSession(oldSession);
      }
    }
    finally {
      sessionScope.exit();
    }
  }

  private void notifyAfterSessionStart(MavenSession mavenSession) {
    try {
      for (AbstractMavenLifecycleParticipant listener : getLifecycleParticipants(Collections.emptyList())) {
        listener.afterSessionStart(mavenSession);
      }
    }
    catch (MavenExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
   */
  private void loadExtensions(MavenProject project, List<Exception> exceptions) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants = getLifecycleParticipants(Collections.singletonList(project));
    if (!lifecycleParticipants.isEmpty()) {
      LegacySupport legacySupport = getComponent(LegacySupport.class);
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
   * adapted from {@link DefaultMaven#getLifecycleParticipants(Collection)}
   */
  private Collection<AbstractMavenLifecycleParticipant> getLifecycleParticipants(Collection<MavenProject> projects) {
    Collection<AbstractMavenLifecycleParticipant> lifecycleListeners = new LinkedHashSet<>();

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      try {
        lifecycleListeners.addAll(getContainer().lookupList(AbstractMavenLifecycleParticipant.class));
      }
      catch (ComponentLookupException e) {
        // this is just silly, lookupList should return an empty list!
        warn("Failed to lookup lifecycle participants", e);
      }

      Collection<ClassLoader> scannedRealms = new HashSet<>();

      for (MavenProject project : projects) {
        ClassLoader projectRealm = project.getClassRealm();

        if (projectRealm != null && scannedRealms.add(projectRealm)) {
          Thread.currentThread().setContextClassLoader(projectRealm);

          try {
            lifecycleListeners.addAll(getContainer().lookupList(AbstractMavenLifecycleParticipant.class));
          }
          catch (ComponentLookupException e) {
            // this is just silly, lookupList should return an empty list!
            warn("Failed to lookup lifecycle participants", e);
          }
        }
      }
    }
    finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    return lifecycleListeners;
  }

  private static void warn(String message, Throwable e) {
    MavenServerGlobals.getLogger().warn(new RuntimeException(message, e));
  }

  private PlexusContainer getContainer() {
    return myContainer;
  }

  @NotNull
  private MavenSession createMavenSession(MavenExecutionRequest request, DefaultMaven maven) {
    RepositorySystemSession repositorySession = maven.newRepositorySession(request);
    request.getProjectBuildingRequest().setRepositorySession(repositorySession);
    return new MavenSession(getContainer(), repositorySession, request, new DefaultMavenExecutionResult());
  }

  @NotNull
  private List<ProjectBuildingResult> getProjectBuildingResults(@NotNull MavenExecutionRequest request, @NotNull Collection<File> files) {
    ProjectBuilder builder = getComponent(ProjectBuilder.class);

    ModelInterpolator modelInterpolator = getComponent(ModelInterpolator.class);

    String savedLocalRepository = null;
/*    if (modelInterpolator instanceof CustomMaven3ModelInterpolator2) {
      CustomMaven3ModelInterpolator2 customMaven3ModelInterpolator2 = (CustomMaven3ModelInterpolator2)modelInterpolator;
      savedLocalRepository = customMaven3ModelInterpolator2.getLocalRepository();
      customMaven3ModelInterpolator2.setLocalRepository(request.getLocalRepositoryPath().getAbsolutePath());
    }*/


    List<ProjectBuildingResult> buildingResults = new ArrayList<>();

    ProjectBuildingRequest projectBuildingRequest = request.getProjectBuildingRequest();
    projectBuildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
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

  private void buildSinglePom(ProjectBuilder builder,
                              List<ProjectBuildingResult> buildingResults,
                              ProjectBuildingRequest projectBuildingRequest,
                              File pomFile) {
    try {
      ProjectBuildingResult build = builder.build(pomFile, projectBuildingRequest);
      buildingResults.add(build);
    }
    catch (ProjectBuildingException e) {
      handleProjectBuildingException(buildingResults, e);
    }
  }

  private void handleProjectBuildingException(List<ProjectBuildingResult> buildingResults, ProjectBuildingException e) {
    List<ProjectBuildingResult> results = e.getResults();
    if (results != null && !results.isEmpty()) {
      buildingResults.addAll(results);
    }
    else {
      Throwable cause = e.getCause();
      List<ModelProblem> problems = null;
      if (cause instanceof ModelBuildingException) {
        problems = ((ModelBuildingException)cause).getProblems();
      }
      buildingResults.add(new MyProjectBuildingResult(null, e.getPomFile(), null, problems, null));
    }
  }

  private static class MyProjectBuildingResult implements ProjectBuildingResult {

    private final String myProjectId;
    private final File myPomFile;
    private final MavenProject myMavenProject;
    private final List<ModelProblem> myProblems;
    private final DependencyResolutionResult myDependencyResolutionResult;

    MyProjectBuildingResult(String projectId,
                            File pomFile,
                            MavenProject mavenProject,
                            List<ModelProblem> problems,
                            DependencyResolutionResult dependencyResolutionResult) {
      myProjectId = projectId;
      myPomFile = pomFile;
      myMavenProject = mavenProject;
      myProblems = problems;
      myDependencyResolutionResult = dependencyResolutionResult;
    }

    @Override
    public String getProjectId() {
      return myProjectId;
    }

    @Override
    public File getPomFile() {
      return myPomFile;
    }

    @Override
    public MavenProject getProject() {
      return myMavenProject;
    }

    @Override
    public List<ModelProblem> getProblems() {
      return myProblems;
    }

    @Override
    public DependencyResolutionResult getDependencyResolutionResult() {
      return myDependencyResolutionResult;
    }
  }

  private static void fillSessionCache(MavenSession mavenSession,
                                       RepositorySystemSession session,
                                       List<ProjectBuildingResult> buildingResults) {
    if (session instanceof DefaultRepositorySystemSession) {
      int initialCapacity = (int)(buildingResults.size() * 1.5);
      Map<MavenId, Model> cacheMavenModelMap = new HashMap<MavenId, Model>(initialCapacity);
      Map<String, MavenProject> mavenProjectMap = new HashMap<String, MavenProject>(initialCapacity);
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


  @Override
  public List<PluginResolutionResponse> resolvePlugins(@NotNull Collection<PluginResolutionRequest> pluginResolutionRequests,
                                                       MavenToken token) {
    MavenServerUtil.checkToken(token);

    MavenExecutionRequest request = createRequest(null, null, null);
    request.setTransferListener(new Maven40TransferListenerAdapter(myCurrentIndicator));

    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    RepositorySystemSession session = maven.newRepositorySession(request);
    myImporterSpy.setIndicator(myCurrentIndicator);

    List<PluginResolutionData> resolutions = new ArrayList<>();

    for (PluginResolutionRequest pluginResolutionRequest : pluginResolutionRequests) {
      MavenId mavenPluginId = pluginResolutionRequest.getMavenPluginId();
      int nativeMavenProjectId = pluginResolutionRequest.getNativeMavenProjectId();

      String groupId = mavenPluginId.getGroupId();
      String artifactId = mavenPluginId.getArtifactId();

      MavenProject project = RemoteNativeMaven40ProjectHolder.findProjectById(nativeMavenProjectId);
      List<RemoteRepository> remoteRepos = project.getRemotePluginRepositories();

      Plugin pluginFromProject = project.getBuild().getPluginsAsMap().get(groupId + ':' + artifactId);
      List<org.apache.maven.model.Dependency> pluginDependencies =
        null == pluginFromProject ? Collections.emptyList() : pluginFromProject.getDependencies();

      PluginResolutionData resolution = new PluginResolutionData(mavenPluginId, pluginDependencies, remoteRepos);
      resolutions.add(resolution);
    }

    boolean runInParallel = false;//canResolveDependenciesInParallel();
    List<PluginResolutionResponse> results =
      MavenServerParallelRunner.execute(runInParallel, resolutions, resolution ->
        resolvePlugin(resolution.mavenPluginId, resolution.pluginDependencies, resolution.remoteRepos, session)
      );

    return results;
  }

  private static class PluginResolutionData {
    MavenId mavenPluginId;
    List<org.apache.maven.model.Dependency> pluginDependencies;
    List<RemoteRepository> remoteRepos;

    private PluginResolutionData(MavenId mavenPluginId,
                                 List<org.apache.maven.model.Dependency> pluginDependencies,
                                 List<RemoteRepository> remoteRepos) {
      this.mavenPluginId = mavenPluginId;
      this.pluginDependencies = pluginDependencies;
      this.remoteRepos = remoteRepos;
    }
  }

  @NotNull
  private PluginResolutionResponse resolvePlugin(MavenId mavenPluginId,
                                                 List<org.apache.maven.model.Dependency> pluginDependencies,
                                                 List<RemoteRepository> remoteRepos,
                                                 RepositorySystemSession session) {
    List<MavenArtifact> artifacts = new ArrayList<>();

    try {
      Plugin plugin = new Plugin();
      plugin.setGroupId(mavenPluginId.getGroupId());
      plugin.setArtifactId(mavenPluginId.getArtifactId());
      plugin.setVersion(mavenPluginId.getVersion());
      plugin.setDependencies(pluginDependencies);

      PluginDependenciesResolver pluginDependenciesResolver = getPluginDependenciesResolver();

      org.eclipse.aether.artifact.Artifact pluginArtifact =
        pluginDependenciesResolver.resolve(plugin, remoteRepos, session);

      DependencyNode node = pluginDependenciesResolver
        .resolve(plugin, pluginArtifact, null, remoteRepos, session);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);


      for (org.eclipse.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
        if (!Objects.equals(artifact.getArtifactId(), plugin.getArtifactId()) ||
            !Objects.equals(artifact.getGroupId(), plugin.getGroupId())) {
          artifacts.add(Maven40ModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile()));
        }
      }

      return new PluginResolutionResponse(mavenPluginId, true, artifacts);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn(e);
      return new PluginResolutionResponse(mavenPluginId, false, artifacts);
    }
  }

  @NotNull
  private PluginDependenciesResolver getPluginDependenciesResolver() {
    PluginDependenciesResolver dependenciesResolver = myPluginDependenciesResolver.get();
    if (dependenciesResolver != null) return dependenciesResolver;
    return myPluginDependenciesResolver.updateAndGet(value -> value == null ? createPluginDependenciesResolver() : value);
  }

  // TODO: useCustomDependenciesResolver
  @NotNull
  protected PluginDependenciesResolver createPluginDependenciesResolver() {
    return getComponent(PluginDependenciesResolver.class);
  }


  @Nullable
  @Override
  public String evaluateEffectivePom(@NotNull File file,
                                     @NotNull List<String> activeProfiles,
                                     @NotNull List<String> inactiveProfiles,
                                     MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven40EffectivePomDumper.evaluateEffectivePom(this, file, activeProfiles, inactiveProfiles);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }


  @NotNull
  @Override
  public List<MavenGoalExecutionResult> executeGoal(@NotNull String longRunningTaskId,
                                                    @NotNull Collection<MavenGoalExecutionRequest> requests,
                                                    @NotNull String goal,
                                                    MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = new LongRunningTask(longRunningTaskId, requests.size())) {
      return executeGoal(task, requests, goal);
    }
  }

  private List<MavenGoalExecutionResult> executeGoal(@NotNull LongRunningTask task,
                                                     @NotNull Collection<MavenGoalExecutionRequest> requests,
                                                     @NotNull String goal) {
    try {
      List<MavenGoalExecutionResult> results = new ArrayList<>();
      for (MavenGoalExecutionRequest request : requests) {
        if (task.isCanceled()) break;
        MavenGoalExecutionResult result = doExecute(request, goal);
        results.add(result);
        task.incrementFinishedRequests();
      }
      return results;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private MavenGoalExecutionResult doExecute(@NotNull MavenGoalExecutionRequest request, @NotNull String goal) {
    File file = request.file();
    MavenExplicitProfiles profiles = request.profiles();
    List<String> activeProfiles = new ArrayList<>(profiles.getEnabledProfiles());
    List<String> inactiveProfiles = new ArrayList<>(profiles.getDisabledProfiles());
    MavenExecutionRequest mavenExecutionRequest = createRequest(file, activeProfiles, inactiveProfiles);
    mavenExecutionRequest.setGoals(Collections.singletonList(goal));

    Maven maven = getComponent(Maven.class);
    MavenExecutionResult executionResult = maven.execute(mavenExecutionRequest);

    Maven40ExecutionResult result = new Maven40ExecutionResult(executionResult.getProject(), filterExceptions(executionResult.getExceptions()));
    return createEmbedderExecutionResult(file, result);
  }

  @NotNull
  private MavenGoalExecutionResult createEmbedderExecutionResult(@NotNull File file, Maven40ExecutionResult result) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();

    collectProblems(file, result.getExceptions(), result.getModelProblems(), problems);

    MavenGoalExecutionResult.Folders folders = new MavenGoalExecutionResult.Folders();
    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenGoalExecutionResult(false, file, folders, problems);

    folders.setSources(mavenProject.getCompileSourceRoots());
    folders.setTestSources(mavenProject.getTestCompileSourceRoots());
    folders.setResources(Maven40ModelConverter.convertResources(mavenProject.getModel().getBuild().getResources()));
    folders.setTestResources(Maven40ModelConverter.convertResources(mavenProject.getModel().getBuild().getTestResources()));

    return new MavenGoalExecutionResult(true, file, folders, problems);
  }

  private static List<Exception> filterExceptions(List<Throwable> list) {
    for (Throwable throwable : list) {
      if (!(throwable instanceof Exception)) {
        throw new RuntimeException(throwable);
      }
    }

    return (List<Exception>)((List)list);
  }


  @Override
  @Nullable
  public MavenModel readModel(File file, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Map<String, Object> inputOptions = new HashMap<>();
      inputOptions.put(ModelProcessor.SOURCE, new FileModelSource(file));

      ModelReader reader = null;
      if (!StringUtilRt.endsWithIgnoreCase(file.getName(), "xml")) {
        try {
          Object polyglotManager = myContainer.lookup("org.sonatype.maven.polyglot.PolyglotModelManager");
          if (polyglotManager != null) {
            Method getReaderFor = polyglotManager.getClass().getMethod("getReaderFor", Map.class);
            reader = (ModelReader)getReaderFor.invoke(polyglotManager, inputOptions);
          }
        }
        catch (ComponentLookupException ignore) {
        }
        catch (Throwable e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }

      if (reader == null) {
        try {
          reader = myContainer.lookup(ModelReader.class);
        }
        catch (ComponentLookupException ignore) {
        }
      }
      if (reader != null) {
        try {
          Model model = reader.read(file, inputOptions);
          return Maven40ModelConverter.convertModel(model);
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn(e);
    }
    return null;
  }


  @Override
  public void release(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      myContainer.dispose();
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }


  @NotNull
  @Override
  public List<MavenArtifact> resolveArtifacts(@NotNull String longRunningTaskId,
                                              @NotNull Collection<MavenArtifactResolutionRequest> requests,
                                              MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = new LongRunningTask(longRunningTaskId, requests.size())) {
      return doResolveArtifacts(task, requests);
    }
  }

  @NotNull
  private List<MavenArtifact> doResolveArtifacts(@NotNull LongRunningTask task,
                                                 @NotNull Collection<MavenArtifactResolutionRequest> requests) {
    try {
      List<MavenArtifact> artifacts = new ArrayList<>();
      for (MavenArtifactResolutionRequest request : requests) {
        if (task.isCanceled()) break;
        MavenArtifact artifact = doResolveArtifact(request.getArtifactInfo(), request.getRemoteRepositories());
        artifacts.add(artifact);
        task.incrementFinishedRequests();
      }
      return artifacts;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private MavenArtifact doResolveArtifact(MavenArtifactInfo info, List<MavenRemoteRepository> remoteRepositories) {
    Artifact resolved = doResolveArtifact(createArtifact(info), convertRepositories(remoteRepositories));
    return Maven40ModelConverter.convertArtifact(resolved, getLocalRepositoryFile());
  }

  private Artifact doResolveArtifact(Artifact artifact, List<ArtifactRepository> remoteRepositories) {
    try {
      MavenExecutionRequest request =
        createRequest(null, null, null);
      for (ArtifactRepository artifactRepository : remoteRepositories) {
        request.addRemoteRepository(artifactRepository);
      }

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession repositorySystemSession = maven.newRepositorySession(request);

      initLogging(myConsoleWrapper);

      // do not use request.getRemoteRepositories() here,
      // it can be broken after DefaultMaven#newRepositorySession => MavenRepositorySystem.injectMirror invocation
      RemoteRepositoryManager remoteRepositoryManager = getComponent(RemoteRepositoryManager.class);
      org.eclipse.aether.RepositorySystem repositorySystem = getComponent(org.eclipse.aether.RepositorySystem.class);
      List<RemoteRepository> repositories = RepositoryUtils.toRepos(remoteRepositories);
      repositories =
        remoteRepositoryManager.aggregateRepositories(repositorySystemSession, new ArrayList<>(), repositories, false);

      ArtifactResult artifactResult = repositorySystem.resolveArtifact(
        repositorySystemSession, new ArtifactRequest(RepositoryUtils.toArtifact(artifact), repositories, null));

      return RepositoryUtils.toArtifact(artifactResult.getArtifact());
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().info(e);
    }
    return artifact;
  }

  private void initLogging(Maven40ServerConsoleLogger consoleWrapper) {
    Maven40Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }

  @NotNull
  private List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) {
    List<ArtifactRepository> result = map2ArtifactRepositories(repositories);
    if (getComponent(LegacySupport.class).getRepositorySession() == null) {
      myRepositorySystem.injectMirror(result, myMavenSettings.getMirrors());
      myRepositorySystem.injectProxy(result, myMavenSettings.getProxies());
      myRepositorySystem.injectAuthentication(result, myMavenSettings.getServers());
    }
    return result;
  }

  private List<ArtifactRepository> map2ArtifactRepositories(List<MavenRemoteRepository> repositories) {
    List<ArtifactRepository> result = new ArrayList<>();
    for (MavenRemoteRepository each : repositories) {
      try {
        result.add(buildArtifactRepository(Maven40ModelConverter.toNativeRepository(each)));
      }
      catch (InvalidRepositoryException e) {
        MavenServerGlobals.getLogger().warn(e);
      }
    }
    return result;
  }

  private ArtifactRepository buildArtifactRepository(Repository repo) throws InvalidRepositoryException {
    RepositorySystem repositorySystem = myRepositorySystem;
    RepositorySystemSession session = getComponent(LegacySupport.class).getRepositorySession();

    ArtifactRepository repository = repositorySystem.buildArtifactRepository(repo);

    if (session != null) {
      repositorySystem.injectMirror(session, Arrays.asList(repository));
      repositorySystem.injectProxy(session, Arrays.asList(repository));
      repositorySystem.injectAuthentication(session, Arrays.asList(repository));
    }

    return repository;
  }

  private Artifact createArtifact(MavenArtifactInfo info) {
    return getComponent(ArtifactFactory.class)
      .createArtifactWithClassifier(info.getGroupId(), info.getArtifactId(), info.getVersion(), info.getPackaging(), info.getClassifier());
  }

  @Override
  public Set<MavenRemoteRepository> resolveRepositories(@NotNull Collection<MavenRemoteRepository> repositories, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return new HashSet<>(
        convertRemoteRepositories(convertRepositories(new ArrayList<>(repositories))));
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }


  @NotNull
  @Override
  public MavenArtifactResolveResult resolveArtifactsTransitively(
    @NotNull List<MavenArtifactInfo> artifacts,
    @NotNull List<MavenRemoteRepository> remoteRepositories,
    MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return resolveArtifactsTransitively(artifacts, remoteRepositories);
    }
    catch (Throwable e) {
      MavenServerGlobals.getLogger().error(e);
      Artifact transferArtifact = getProblemTransferArtifact(e);
      String message = getRootMessage(e);
      MavenProjectProblem problem;
      if (transferArtifact != null) {
        MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(transferArtifact, getLocalRepositoryFile());
        problem = MavenProjectProblem.createRepositoryProblem("", message, true, mavenArtifact);
      }
      else {
        problem = MavenProjectProblem.createStructureProblem("", message);
      }
      return new MavenArtifactResolveResult(Collections.emptyList(), problem);
    }
  }

  private MavenArtifactResolveResult resolveArtifactsTransitively(
    @NotNull List<MavenArtifactInfo> artifacts,
    @NotNull List<MavenRemoteRepository> remoteRepositories) {
    DefaultSessionFactory sessionFactory = getComponent(DefaultSessionFactory.class);
    MavenExecutionRequest request = createRequest(null, null, null);
    request.setRemoteRepositories(convertRepositories(remoteRepositories));
    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    MavenSession mavenSession = createMavenSession(request, maven);
    Session session = sessionFactory.getSession(mavenSession);

    Map<org.apache.maven.api.Artifact, Path> resolvedArtifactMap = new HashMap<>();
    SessionScope sessionScope = getComponent(SessionScope.class);
    try {
      sessionScope.enter();
      sessionScope.seed(DefaultSession.class, (DefaultSession)session);

      for (MavenArtifactInfo mavenArtifactInfo : artifacts) {
        ArtifactCoordinate coordinate = session.createArtifactCoordinate(
          mavenArtifactInfo.getGroupId(),
          mavenArtifactInfo.getArtifactId(),
          mavenArtifactInfo.getVersion(),
          mavenArtifactInfo.getClassifier(),
          mavenArtifactInfo.getPackaging(),
          null);

        ArtifactResolver artifactResolver = session.getService(ArtifactResolver.class);
        ArtifactResolverResult resolved = artifactResolver.resolve(session, Collections.singleton(coordinate));
        resolvedArtifactMap.putAll(resolved.getArtifacts());

        DependencyCoordinate dependencyCoordinate = session.createDependencyCoordinate(coordinate);

        Node dependencyNode = session.collectDependencies(dependencyCoordinate);

        List<DependencyCoordinate> dependencyCoordinates = dependencyNode.stream()
          .filter(node -> node != dependencyNode)
          .filter(node -> node.getDependency() != null)
          .map(node -> node.getDependency().toCoordinate())
          .collect(Collectors.toList());
        ArtifactResolverResult resolvedChildren = artifactResolver.resolve(session, dependencyCoordinates);

        resolvedArtifactMap.putAll(resolvedChildren.getArtifacts());
      }
    }
    finally {
      sessionScope.exit();
    }

    File localRepositoryFile = getLocalRepositoryFile();
    List<MavenArtifact> resolvedArtifacts = new ArrayList<>();
    for (org.apache.maven.api.Artifact apiArtifact : resolvedArtifactMap.keySet()) {
      Path artifactPath = resolvedArtifactMap.get(apiArtifact);
      MavenArtifact mavenArtifact = Maven40ApiModelConverter.convertArtifactAndPath(apiArtifact, artifactPath, localRepositoryFile);
      resolvedArtifacts.add(mavenArtifact);
    }

    return new MavenArtifactResolveResult(resolvedArtifacts, null);
  }


  @Override
  public Collection<MavenArchetype> getLocalArchetypes(MavenToken token, @NotNull String path) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<MavenArchetype> getRemoteArchetypes(MavenToken token, @NotNull String url) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public Map<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId,
                                                              @NotNull String version,
                                                              @NotNull List<MavenRemoteRepository> repositories,
                                                              @Nullable String url, MavenToken token) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }


  @NotNull
  @Override
  public MavenServerPullProgressIndicator customizeAndGetProgressIndicator(@Nullable MavenWorkspaceMap workspaceMap,
                                                                           boolean alwaysUpdateSnapshots, MavenToken token) {
    MavenServerUtil.checkToken(token);

    try {
      // TODO: implement
      //customizeComponents(workspaceMap);

      myWorkspaceMap = workspaceMap;
      myAlwaysUpdateSnapshots = myAlwaysUpdateSnapshots || alwaysUpdateSnapshots;
      myCurrentIndicator = new MavenServerProgressIndicatorWrapper();

      myConsoleWrapper.setWrappee(myCurrentIndicator);
      try {
        UnicastRemoteObject.exportObject(myCurrentIndicator, 0);
      }
      catch (RemoteException e) {
        throw new RuntimeException(e);
      }

      return myCurrentIndicator;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public void reset(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      if (myCurrentIndicator != null) {
        UnicastRemoteObject.unexportObject(myCurrentIndicator, false);
      }
      myCurrentIndicator = null;
      myConsoleWrapper.setWrappee(null);

      // TODO: implement
      //resetCustomizedComponents();
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

}
