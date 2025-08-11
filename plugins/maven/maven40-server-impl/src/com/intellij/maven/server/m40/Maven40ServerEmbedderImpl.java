// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import com.intellij.maven.server.m40.compat.Maven40InvokerRequestFactory;
import com.intellij.maven.server.m40.utils.ExceptionUtils;
import com.intellij.maven.server.m40.utils.Maven40ApiModelConverter;
import com.intellij.maven.server.m40.utils.Maven40EffectivePomDumper;
import com.intellij.maven.server.m40.utils.Maven40ExecutionResult;
import com.intellij.maven.server.m40.utils.Maven40ImporterSpy;
import com.intellij.maven.server.m40.utils.Maven40Invoker;
import com.intellij.maven.server.m40.utils.Maven40ModelConverter;
import com.intellij.maven.server.m40.utils.Maven40ProjectResolver;
import com.intellij.maven.server.m40.utils.Maven40RepositorySystemSessionFactory;
import com.intellij.maven.server.m40.utils.Maven40ServerConsoleLogger;
import com.intellij.maven.server.m40.utils.Maven40Sl4jLoggerWrapper;
import com.intellij.maven.server.m40.utils.Maven40Slf4jServiceProvider;
import com.intellij.maven.server.m40.utils.Maven40TransferListenerAdapter;
import com.intellij.maven.server.m40.utils.Maven40WorkspaceMapReader;
import com.intellij.maven.server.telemetry.MavenServerOpenTelemetry;
import com.intellij.openapi.util.text.StringUtilRt;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.api.*;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.cli.InvokerException;
import org.apache.maven.api.cli.InvokerRequest;
import org.apache.maven.api.cli.Logger;
import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.api.model.Source;
import org.apache.maven.api.services.ArtifactResolver;
import org.apache.maven.api.services.ArtifactResolverResult;
import org.apache.maven.api.services.Lookup;
import org.apache.maven.api.services.model.ModelResolverException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.cling.invoker.ProtoLookup;
import org.apache.maven.cling.invoker.mvn.MavenParser;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProfileActivation;
import org.apache.maven.internal.impl.DefaultSessionFactory;
import org.apache.maven.internal.impl.InternalMavenSession;
import org.apache.maven.jline.JLineMessageBuilderFactory;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.resolver.MavenChainedWorkspaceReader;
import org.apache.maven.resolver.RepositorySystemSessionFactory;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.LongRunningTask;
import org.jetbrains.idea.maven.server.LongRunningTaskInput;
import org.jetbrains.idea.maven.server.MavenArtifactResolutionRequest;
import org.jetbrains.idea.maven.server.MavenArtifactResolveResult;
import org.jetbrains.idea.maven.server.MavenConfigParseException;
import org.jetbrains.idea.maven.server.MavenEmbedderSettings;
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest;
import org.jetbrains.idea.maven.server.MavenGoalExecutionResult;
import org.jetbrains.idea.maven.server.MavenServerConfigUtil;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorImpl;
import org.jetbrains.idea.maven.server.MavenServerEmbeddedBase;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerResponse;
import org.jetbrains.idea.maven.server.MavenServerSettings;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.server.ParallelRunnerForServer;
import org.jetbrains.idea.maven.server.PluginResolutionRequest;
import org.jetbrains.idea.maven.server.PluginResolutionResponse;
import org.jetbrains.idea.maven.server.PomHashMap;
import org.jetbrains.idea.maven.server.ProjectResolutionRequest;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.maven.server.m40.utils.Maven40ModelConverter.convertRemoteRepositories;
import static java.util.Objects.requireNonNull;

public class Maven40ServerEmbedderImpl extends MavenServerEmbeddedBase {
  private final @NotNull Maven40Invoker myMavenInvoker;
  private final @NotNull Lookup myContainer;

  private final Maven40ServerConsoleLogger myConsoleWrapper;

  private final boolean myAlwaysUpdateSnapshots;

  private final @NotNull MavenRepositorySystem myRepositorySystem;

  private final @NotNull Maven40ImporterSpy myImporterSpy;

  protected final @NotNull MavenEmbedderSettings myEmbedderSettings;

  public Maven40ServerEmbedderImpl(MavenEmbedderSettings settings) {
    myEmbedderSettings = settings;

    String mmpDir = settings.getMultiModuleProjectDirectory();
    String multiModuleProjectDirectory = mmpDir == null ? "" : mmpDir;

    MavenServerSettings serverSettings = settings.getSettings();
    String mh = serverSettings.getMavenHomePath();
    String mavenHome = null == mh ? "" : mh;

    myConsoleWrapper = new Maven40ServerConsoleLogger();
    myConsoleWrapper.setThreshold(serverSettings.getLoggingLevel());

    ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    myMavenInvoker = new Maven40Invoker(ProtoLookup.builder().addMapping(ClassWorld.class, classWorld).build());
    String userHomeProperty = System.getProperty("user.home");
    String userHome = userHomeProperty == null ? multiModuleProjectDirectory : userHomeProperty;
    Path mavenHomeDirectory = getCanonicalPath(Paths.get(mavenHome));
    Path userHomeDirectory = getCanonicalPath(Paths.get(userHome));
    Path cwd = getCanonicalPath(Paths.get(multiModuleProjectDirectory));

    List<String> commandLineOptions = new ArrayList<>(serverSettings.getUserProperties().size());
    for (Map.Entry<Object, Object> each : serverSettings.getUserProperties().entrySet()) {
      commandLineOptions.add("-D" + each.getKey() + "=" + each.getValue());
    }

    if (serverSettings.getLocalRepositoryPath() != null) {
      commandLineOptions.add("-Dmaven.repo.local=" + serverSettings.getLocalRepositoryPath());
    }
    if (serverSettings.isUpdateSnapshots()) {
      commandLineOptions.add("-U");
    }

    if (serverSettings.getLoggingLevel() == MavenServerConsoleIndicator.LEVEL_DEBUG) {
      commandLineOptions.add("-X");
      commandLineOptions.add("-e");
    }
    else if (serverSettings.getLoggingLevel() == MavenServerConsoleIndicator.LEVEL_DISABLED) {
      commandLineOptions.add("-q");
    }

    String mavenEmbedderCliOptions = System.getProperty(MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
    if (mavenEmbedderCliOptions != null) {
      commandLineOptions.addAll(StringUtilRt.splitHonorQuotes(mavenEmbedderCliOptions, ' '));
    }

    String globalSettingsPath = serverSettings.getGlobalSettingsPath();
    if (globalSettingsPath != null && new File(globalSettingsPath).isFile()) {
      commandLineOptions.add("-is");
      commandLineOptions.add(globalSettingsPath);
    }
    String userSettingsPath = serverSettings.getUserSettingsPath();
    if (userSettingsPath != null && new File(userSettingsPath).isFile()) {
      commandLineOptions.add("-s");
      commandLineOptions.add(userSettingsPath);
    }

    if (serverSettings.isOffline()) {
      commandLineOptions.add("-o");
    }

    // configure our logger
    // raw streams are needed because otherwise in org.apache.maven.cling.invoker.LookupInvoker.doConfigureWithTerminal
    // the logger will be cast to MavenSimpleLogger resulting in ClassCastException:
    //         if (options.rawStreams().isEmpty() || !options.rawStreams().get()) {
    //            MavenSimpleLogger stdout = (MavenSimpleLogger) context.loggerFactory.getLogger("stdout");
    //            MavenSimpleLogger stderr = (MavenSimpleLogger) context.loggerFactory.getLogger("stderr");
    System.setProperty("slf4j.provider", Maven40Slf4jServiceProvider.class.getName());
    commandLineOptions.add("-raw-streams");
    commandLineOptions.add("true");
    initLogging(myConsoleWrapper);

    ParserRequest parserRequest = ParserRequest.builder(
        "",
        "",
        commandLineOptions,
        new JLineMessageBuilderFactory()
      )
      .userHome(userHomeDirectory)
      .mavenHome(mavenHomeDirectory)
      .cwd(cwd)
      .build();

    MavenParser mavenParser = new MavenParser() {
      @Override
      public Path getRootDirectory(LocalContext context) {
        Path rootDir = super.getRootDirectory(context);
        if (null == rootDir) {
          Path topDirectory = context.topDirectory;
          MavenServerGlobals.getLogger().warn("Root dir not found for " + topDirectory);
          return topDirectory;
        }
        return rootDir;
      }
    };
    InvokerRequest invokerRequest;
    List<Logger.Entry> entries = new ArrayList<>();
    try {
      invokerRequest = Maven40InvokerRequestFactory.createProxy(mavenParser.parseInvocation(parserRequest));
      entries.addAll(invokerRequest.parserRequest().logger().drain());
      myContainer = myMavenInvoker.invokeAndGetContext(invokerRequest).lookup;
    }
    catch (InvokerException.ExitException e) {
      StringBuilder message = new StringBuilder(e.getMessage());
      for (var entry : entries) {
        if (entry.level() == Logger.Level.ERROR) {
          message.append("\n").append(entry.error().getMessage());
        }
      }
      throw new MavenConfigParseException(message.toString(), multiModuleProjectDirectory);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    if(myContainer == null) throw new IllegalStateException("Cannot create maven container");

    myAlwaysUpdateSnapshots = commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots");

    Map<String, String> mySystemProperties = invokerRequest.systemProperties();
    if (serverSettings.getProjectJdk() != null) {
      mySystemProperties.put("java.home", serverSettings.getProjectJdk());
    }

    myRepositorySystem = getComponent(MavenRepositorySystem.class);

    Maven40ImporterSpy importerSpy = getComponentIfExists(Maven40ImporterSpy.class);

    if (importerSpy == null) {
      importerSpy = new Maven40ImporterSpy();
      //TODO: importer spy
      //myContainer.addComponent(importerSpy, Maven40ImporterSpy.class.getName());
    }
    myImporterSpy = importerSpy;
  }

  @Override
  public @NotNull MavenServerResponse<ArrayList<MavenServerExecutionResult>> resolveProjects(@NotNull LongRunningTaskInput longRunningTaskInput,
                                                                                             @NotNull ProjectResolutionRequest request,
                                                                                             MavenToken token) {
    MavenServerUtil.checkToken(token);
    String longRunningTaskId = longRunningTaskInput.getLongRunningTaskId();
    MavenServerOpenTelemetry telemetry = MavenServerOpenTelemetry.from(longRunningTaskInput.getTelemetryContext());
    List<File> filesToResolve = request.getFilesToResolve();
    PomHashMap pomHashMap = request.getPomHashMap();
    List<String> activeProfiles = request.getActiveProfiles();
    List<String> inactiveProfiles = request.getInactiveProfiles();
    MavenWorkspaceMap workspaceMap = request.getWorkspaceMap();
    boolean updateSnapshots = myAlwaysUpdateSnapshots || request.updateSnapshots();
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, pomHashMap.size(), myConsoleWrapper)) {
      Maven40ProjectResolver projectResolver = new Maven40ProjectResolver(
        this,
        telemetry,
        updateSnapshots,
        myImporterSpy,
        task,
        filesToResolve,
        pomHashMap,
        activeProfiles,
        inactiveProfiles,
        workspaceMap,
        getLocalRepositoryFile(),
        request.getUserProperties(),
        canResolveDependenciesInParallel()
      );
      try {
        customizeComponents(workspaceMap);
        ArrayList<MavenServerExecutionResult> result = telemetry.callWithSpan(
          "projectResolver.resolveProjects", () -> projectResolver.resolveProjects());
        telemetry.shutdown();
        return new MavenServerResponse<>(result, getLongRunningTaskStatus(longRunningTaskId, token));
      }
      finally {
        resetComponents();
      }
    }
  }

  private static boolean canResolveDependenciesInParallel() {
    return true;
  }

  private File getLocalRepositoryFile() {
    return new File(myEmbedderSettings.getSettings().getLocalRepositoryPath());
  }

  public Collection<MavenProjectProblem> collectProblems(@Nullable File file,
                                                         @NotNull Collection<? extends Exception> exceptions,
                                                         @NotNull List<? extends ModelProblem> modelProblems) {
    Collection<MavenProjectProblem> problems = new LinkedHashSet<>();
    for (Throwable each : exceptions) {
      problems.addAll(collectExceptionProblems(file, each));
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
      String message = "Maven model problem: " +
                       problem.getMessage() +
                       " at " +
                       problem.getSource() +
                       ":" +
                       problem.getLineNumber() +
                       ":" +
                       problem.getColumnNumber();
      if (problem.getSeverity() == ModelProblem.Severity.ERROR) {
        myConsoleWrapper.error(message);
      }
      else {
        myConsoleWrapper.warn(message);
      }
      Exception problemException = problem.getException();
      if (problemException != null) {
        List<MavenProjectProblem> exceptionProblems = collectExceptionProblems(file, problemException);
        if (exceptionProblems.isEmpty()) {
          myConsoleWrapper.error("Maven model problem", problemException);
          problems.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage()));
        }
        else {
          problems.addAll(exceptionProblems);
        }
      }
      else {
        problems.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage(), false));
      }
    }
    return problems;
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
      result.add(MavenProjectProblem.createRepositoryProblem(path, message, false, mavenArtifact));
    }
    else if (ex instanceof ModelResolverException) {
      myConsoleWrapper.error("Maven resolver problem", ex);
      result.add(MavenProjectProblem.createStructureProblem(path, ex.getMessage(), false));
    }
    else {
      myConsoleWrapper.error("Maven server structure problem", ex);
      result.add(MavenProjectProblem.createStructureProblem(path, getRootMessage(ex), false));
    }
    return result;
  }

  public static @NotNull String getRootMessage(Throwable each) {
    String baseMessage = each.getMessage() != null ? each.getMessage() : "";
    Throwable rootCause = ExceptionUtils.getRootCause(each);
    String rootMessage = rootCause != null ? rootCause.getMessage() : "";
    return isNotEmpty(rootMessage) ? rootMessage : baseMessage;
  }

  private static boolean isNotEmpty(String str) {
    return ((str != null) && (!str.isEmpty()));
  }

  private static @Nullable Artifact getProblemTransferArtifact(Throwable each) {
    Throwable[] throwables = ExceptionUtils.getThrowables(each);
    for (Throwable throwable : throwables) {
      if (throwable instanceof ArtifactTransferException) {
        return RepositoryUtils.toArtifact(((ArtifactTransferException)throwable).getArtifact());
      }
    }
    return null;
  }

  @SuppressWarnings({"unchecked"})
  private <T> T getComponent(Class<T> clazz, String roleHint) {
    return myContainer.lookup(clazz, roleHint);
  }

  @SuppressWarnings("unchecked")
  public <T> T getComponent(Class<T> clazz) {
    return myContainer.lookup(clazz);
  }

  private <T> T getComponentIfExists(Class<T> clazz) {
    return myContainer.lookupOptional(clazz).orElse(null);
  }

  private <T> T getComponentIfExists(Class<T> clazz, String roleHint) {
    return myContainer.lookupOptional(clazz, roleHint).orElse(null);
  }

  public MavenExecutionRequest createRequest(File file,
                                             List<String> activeProfiles,
                                             List<String> inactiveProfiles) {
    return createRequest(file, activeProfiles, inactiveProfiles, new Properties());
  }

  public MavenExecutionRequest createRequest(@Nullable File file,
                                             @Nullable List<String> activeProfiles,
                                             @Nullable List<String> inactiveProfiles,
                                             @NotNull Properties customProperties) {
    try {
      MavenExecutionRequest request = myMavenInvoker.createMavenExecutionRequest();

      // Consider creating a new MavenInvoker / MavenContext / MavenInvokerRequest for every call to the Embedder.
      // Then profiles will be activated by the MavenInvoker, and this extra step won't be needed.
      // Similarly, user properties will be handled by the MavenInvoker.
      activateProfiles(activeProfiles, inactiveProfiles, request);
      Properties userProperties = request.getUserProperties();
      if (file != null) {
        userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigPropertiesForNestedProjectDir(file.getParentFile()));
      }
      userProperties.putAll(customProperties);

      return request;
    }
    catch (Exception e) {
      warn(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  private static void activateProfiles(@Nullable List<String> activeProfiles,
                                       @Nullable List<String> inactiveProfiles,
                                       MavenExecutionRequest request) {
    ProfileActivation profileActivation = request.getProfileActivation();
    if (null != activeProfiles) {
      for (String profileId : activeProfiles) {
        profileActivation.addProfileActivation(profileId, true, false);
      }
    }
    if (null != inactiveProfiles) {
      for (String profileId : inactiveProfiles) {
        profileActivation.addProfileActivation(profileId, false, false);
      }
    }
  }

  /**
   * adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
   */
  public MavenExecutionResult executeWithMavenSession(@NotNull MavenExecutionRequest request,
                                                      @NotNull MavenWorkspaceMap workspaceMap,
                                                      @NotNull MavenServerConsoleIndicatorImpl indicator,
                                                      Consumer<MavenSession> runnable) {
    RepositorySystemSessionFactory rsf = getComponent(RepositorySystemSessionFactory.class);
    Maven40RepositorySystemSessionFactory irsf = new Maven40RepositorySystemSessionFactory(
      rsf,
      workspaceMap,
      indicator
    );
    WorkspaceReader workspaceReader = new Maven40WorkspaceMapReader(workspaceMap);
    WorkspaceReader ideWorkspaceReader = getComponentIfExists(WorkspaceReader.class, "ide");
    SessionScope sessionScope = getComponent(SessionScope.class);
    DefaultSessionFactory defaultSessionFactory = getComponent(DefaultSessionFactory.class);
    LegacySupport legacySupport = getComponent(LegacySupport.class);

    DefaultMavenExecutionResult result = new DefaultMavenExecutionResult();

    // https://youtrack.jetbrains.com/issue/IDEA-356125
    // Temporary solution for Maven 4
    // There's a race condition between sessionScope.enter and sessionScope.seed
    // in sessionScope.enter a new ScopeState is added to the beginning of List<ScopeState> values
    // in sessionScope.seed values[0] is modified
    // Consider creating a new MavenInvoker / MavenContext / MavenInvokerRequest for every call to the Embedder.
    synchronized (this) {
      sessionScope.enter();
      MavenChainedWorkspaceReader chainedWorkspaceReader =
        new MavenChainedWorkspaceReader(workspaceReader, ideWorkspaceReader);
      try (RepositorySystemSession.CloseableSession closeableSession = newCloseableSession(request, chainedWorkspaceReader, irsf)) {
        MavenSession session = new MavenSession(closeableSession, request, result);
        session.setSession(defaultSessionFactory.newSession(session));

        sessionScope.seed(MavenSession.class, session);
        sessionScope.seed(Session.class, session.getSession());
        sessionScope.seed(InternalMavenSession.class, InternalMavenSession.from(session.getSession()));

        legacySupport.setSession(session);

        afterSessionStart(session);

        runnable.accept(session);
      }
      finally {
        legacySupport.setSession(null);
        sessionScope.exit();
      }
    }

    return result;
  }

  private static RepositorySystemSession.CloseableSession newCloseableSession(MavenExecutionRequest request,
                                                                              WorkspaceReader workspaceReader,
                                                                              RepositorySystemSessionFactory repositorySessionFactory) {
    return repositorySessionFactory
      .newRepositorySessionBuilder(request)
      .setWorkspaceReader(workspaceReader)
      .build();
  }

  private void afterSessionStart(MavenSession mavenSession) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants =
      getExtensionComponents(Collections.emptyList(), AbstractMavenLifecycleParticipant.class);
    for (AbstractMavenLifecycleParticipant listener : lifecycleParticipants) {
      Thread.currentThread().setContextClassLoader(listener.getClass().getClassLoader());
      try {
        listener.afterSessionStart(mavenSession);
      }
      catch (MavenExecutionException e) {
        throw new RuntimeException(e);
      }
      finally {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
    }
  }

  /**
   * adapted from {@link DefaultMaven#getExtensionComponents(Collection, Class)} as of 10.01.2024
   */
  public <T> Collection<T> getExtensionComponents(Collection<MavenProject> projects, Class<T> role) {
    Collection<T> foundComponents = new LinkedHashSet<>(getContainer().lookupList(role));
    foundComponents.addAll(getProjectScopedExtensionComponents(projects, role));
    return foundComponents;
  }

  protected <T> Collection<T> getProjectScopedExtensionComponents(Collection<MavenProject> projects, Class<T> role) {
    if (projects == null) {
      return Collections.emptyList();
    }

    Collection<T> foundComponents = new LinkedHashSet<>();
    Collection<ClassLoader> scannedRealms = new HashSet<>();

    Thread currentThread = Thread.currentThread();
    ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    try {
      for (MavenProject project : projects) {
        ClassLoader projectRealm = project.getClassRealm();

        if (projectRealm != null && scannedRealms.add(projectRealm)) {
          currentThread.setContextClassLoader(projectRealm);
          foundComponents.addAll(getContainer().lookupList(role));
        }
      }
      return foundComponents;
    }
    finally {
      currentThread.setContextClassLoader(originalContextClassLoader);
    }
  }

  private static void warn(String message, Throwable e) {
    MavenServerGlobals.getLogger().warn(new RuntimeException(message, e));
  }

  private @NotNull Lookup getContainer() {
    return myContainer;
  }

  @Override
  public MavenServerResponse<ArrayList<PluginResolutionResponse>> resolvePlugins(@NotNull LongRunningTaskInput longRunningTaskInput,
                                                                                 @NotNull ArrayList<PluginResolutionRequest> pluginResolutionRequests,
                                                                                 boolean forceUpdateSnapshots,
                                                                                 MavenToken token) {
    MavenServerUtil.checkToken(token);
    String longRunningTaskId = longRunningTaskInput.getLongRunningTaskId();
    MavenServerOpenTelemetry telemetry = MavenServerOpenTelemetry.from(longRunningTaskInput.getTelemetryContext());


    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, pluginResolutionRequests.size(), myConsoleWrapper)) {
      MavenExecutionRequest request = createRequest(null, null, null);
      request.setTransferListener(new Maven40TransferListenerAdapter(task.getIndicator()));
      request.setUpdateSnapshots(myAlwaysUpdateSnapshots || forceUpdateSnapshots);


      List<PluginResolutionResponse> results = new ArrayList<>();
      executeWithMavenSession(request, MavenWorkspaceMap.empty(), task.getIndicator(), session -> {
        List<PluginResolutionData> resolutions = collectPluginResolutionData(session, pluginResolutionRequests);
        results.addAll(ParallelRunnerForServer.execute(false, resolutions, resolution ->
          resolvePlugin(task, resolution.mavenPluginId, resolution.resolveDependencies, resolution.dependencies, resolution.remoteRepos,
                        session.getRepositorySession())));
      });

      telemetry.shutdown();
      // IDEA-341451: Parallel plugin resolution hangs in Maven 4.0.0-alpha-9
      // It worked fine up until Maven 4.0.0-alpha-8
      return new MavenServerResponse<>(new ArrayList<>(results), getLongRunningTaskStatus(longRunningTaskId, token));
    }
  }

  private @NotNull List<PluginResolutionData> collectPluginResolutionData(MavenSession session,
                                                                          @NotNull ArrayList<PluginResolutionRequest> pluginResolutionRequests) {
    List<PluginResolutionData> resolutions = new ArrayList<>();

    for (PluginResolutionRequest pluginResolutionRequest : pluginResolutionRequests) {
      MavenId mavenPluginId = pluginResolutionRequest.getMavenPluginId();
      List<RemoteRepository> remoteRepos =
        RepositoryUtils.toRepos(map2ArtifactRepositories(session, pluginResolutionRequest.getRepositories(), false));

      List<Dependency> dependencies = new ArrayList<>();
      for (MavenId dependencyId : pluginResolutionRequest.getPluginDependencies()) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(dependencyId.getGroupId());
        dependency.setArtifactId(dependencyId.getArtifactId());
        dependency.setVersion(dependencyId.getVersion());
        dependencies.add(dependency);
      }

      PluginResolutionData resolution = new PluginResolutionData(
        mavenPluginId,
        pluginResolutionRequest.resolvePluginDependencies(),
        dependencies,
        remoteRepos);
      resolutions.add(resolution);
    }
    return resolutions;
  }

  private static class PluginResolutionData {
    MavenId mavenPluginId;
    boolean resolveDependencies;
    List<Dependency> dependencies;
    List<RemoteRepository> remoteRepos;

    private PluginResolutionData(MavenId mavenPluginId,
                                 boolean resolveDependencies,
                                 List<Dependency> dependencies,
                                 List<RemoteRepository> remoteRepos) {
      this.mavenPluginId = mavenPluginId;
      this.resolveDependencies = resolveDependencies;
      this.remoteRepos = remoteRepos;
      this.dependencies = dependencies;
    }
  }

  private @NotNull PluginResolutionResponse resolvePlugin(LongRunningTask task,
                                                          MavenId mavenPluginId,
                                                          boolean resolveDependencies,
                                                          List<Dependency> dependencies,
                                                          List<RemoteRepository> remoteRepos,
                                                          RepositorySystemSession session) {
    long startTime = System.currentTimeMillis();
    MavenArtifact mavenPluginArtifact = null;
    List<MavenArtifact> artifacts = new ArrayList<>();
    if (task.isCanceled()) return new PluginResolutionResponse(mavenPluginId, mavenPluginArtifact, artifacts);

    try {
      Plugin plugin = new Plugin();
      plugin.setGroupId(mavenPluginId.getGroupId());
      plugin.setArtifactId(mavenPluginId.getArtifactId());
      plugin.setVersion(mavenPluginId.getVersion());
      plugin.setDependencies(dependencies);

      PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);

      org.eclipse.aether.artifact.Artifact pluginArtifact =
        pluginDependenciesResolver.resolve(plugin, remoteRepos, session);

      DependencyFilter dependencyFilter = resolveDependencies ? null : new DependencyFilter() {
        @Override
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
          return false;
        }
      };

      DependencyNode node = pluginDependenciesResolver.resolve(plugin, pluginArtifact, dependencyFilter, remoteRepos, session);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);


      for (org.eclipse.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
        MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile());
        if (!Objects.equals(artifact.getArtifactId(), plugin.getArtifactId()) ||
            !Objects.equals(artifact.getGroupId(), plugin.getGroupId())) {
          artifacts.add(mavenArtifact);
        }
        else {
          mavenPluginArtifact = mavenArtifact;
        }
      }

      task.incrementFinishedRequests();
      return new PluginResolutionResponse(mavenPluginId, mavenPluginArtifact, artifacts);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn(e);
      return new PluginResolutionResponse(mavenPluginId, mavenPluginArtifact, artifacts);
    }
    finally {
      long totalTime = System.currentTimeMillis() - startTime;
      MavenServerGlobals.getLogger().debug("Resolved plugin " + mavenPluginId + " in " + totalTime + " ms");
    }
  }

  @Override
  public @NotNull MavenServerResponse<@NotNull String> evaluateEffectivePom(@NotNull LongRunningTaskInput longRunningTaskInput,
                                                                            @NotNull File file,
                                                                            @NotNull ArrayList<String> activeProfiles,
                                                                            @NotNull ArrayList<String> inactiveProfiles,
                                                                            MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      String longRunningTaskId = longRunningTaskInput.getLongRunningTaskId();
      try (LongRunningTask task = newLongRunningTask(longRunningTaskId, 1, myConsoleWrapper)) {
        String result = Maven40EffectivePomDumper.evaluateEffectivePom(this, task.getIndicator(), file, activeProfiles, inactiveProfiles);
        task.incrementFinishedRequests();
        return new MavenServerResponse<>(result, getLongRunningTaskStatus(longRunningTaskId, token));
      }
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public @NotNull MavenServerResponse<ArrayList<MavenGoalExecutionResult>> executeGoal(@NotNull LongRunningTaskInput longRunningTaskInput,
                                                                                       @NotNull ArrayList<MavenGoalExecutionRequest> requests,
                                                                                       @NotNull String goal,
                                                                                       MavenToken token) {
    MavenServerUtil.checkToken(token);
    String longRunningTaskId = longRunningTaskInput.getLongRunningTaskId();
    MavenServerOpenTelemetry telemetry = MavenServerOpenTelemetry.from(longRunningTaskInput.getTelemetryContext());
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      ArrayList<MavenGoalExecutionResult> results = executeGoal(task, requests, goal);
      telemetry.shutdown();
      return new MavenServerResponse<>(results, getLongRunningTaskStatus(longRunningTaskId, token));
    }
  }

  private ArrayList<MavenGoalExecutionResult> executeGoal(@NotNull LongRunningTask task,
                                                          @NotNull Collection<MavenGoalExecutionRequest> requests,
                                                          @NotNull String goal) {
    try {
      ArrayList<MavenGoalExecutionResult> results = new ArrayList<>();
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

    Properties userProperties = request.userProperties();
    mavenExecutionRequest.setUserProperties(userProperties);

    List<String> selectedProjects = request.selectedProjects();
    if (!selectedProjects.isEmpty()) {
      mavenExecutionRequest.setSelectedProjects(selectedProjects);
    }

    Maven maven = getComponent(Maven.class);
    MavenExecutionResult executionResult = maven.execute(mavenExecutionRequest);

    Maven40ExecutionResult result =
      new Maven40ExecutionResult(executionResult.getProject(), filterExceptions(executionResult.getExceptions()));
    return createEmbedderExecutionResult(file, result);
  }

  private @NotNull MavenGoalExecutionResult createEmbedderExecutionResult(@NotNull File file, Maven40ExecutionResult result) {
    Collection<MavenProjectProblem> problems = collectProblems(file, result.getExceptions(), Collections.emptyList());

    MavenGoalExecutionResult.Folders folders = new MavenGoalExecutionResult.Folders();
    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenGoalExecutionResult(false, file, folders, problems);

    folders.setMavenSources(convertSourceRoots(mavenProject.getSourceRoots()));

    return new MavenGoalExecutionResult(true, file, folders, problems);
  }

  private static List<MavenSource> convertSourceRoots(List<Source> roots) {
    List<MavenSource> list = new ArrayList<>();
    for (Source it : roots) {
      MavenSource convert = Maven40ModelConverter.convert(it);
      list.add(convert);
    }
    return list;
  }

  private static List<MavenSource> convertSourceRoots(Collection<SourceRoot> roots) {
    List<MavenSource> list = new ArrayList<>();
    for (SourceRoot it : roots) {
      MavenSource convert = Maven40ModelConverter.convert(it);
      list.add(convert);
    }
    return list;
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
  public @Nullable MavenModel readModel(File file, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      Map<String, Object> inputOptions = new HashMap<>();
      inputOptions.put(ModelProcessor.SOURCE, new FileModelSource(file));

      //TODO:polyglot
/*      if (!StringUtilRt.endsWithIgnoreCase(file.getName(), "xml")) {
        try {
          Object polyglotManager = myContainer.lookup("org.sonatype.maven.polyglot.PolyglotModelManager");
          if (polyglotManager != null) {
            Method getReaderFor = polyglotManager.getClass().getMethod("getReaderFor", Map.class);
            reader = (ModelReader)getReaderFor.invoke(polyglotManager, inputOptions);
          }
        }
        catch (Throwable e) {
          MavenServerGlobals.getLogger().warn(e);
        }
      }*/

      ModelReader reader = myContainer.lookup(ModelReader.class);

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
      myMavenInvoker.close();
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }


  @Override
  public @NotNull MavenServerResponse<ArrayList<MavenArtifact>> resolveArtifacts(@NotNull LongRunningTaskInput longRunningTaskInput,
                                                                                 @NotNull ArrayList<MavenArtifactResolutionRequest> requests,
                                                                                 MavenToken token) {
    MavenServerUtil.checkToken(token);
    String longRunningTaskId = longRunningTaskInput.getLongRunningTaskId();
    MavenServerOpenTelemetry telemetry = MavenServerOpenTelemetry.from(longRunningTaskInput.getTelemetryContext());
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      ArrayList<MavenArtifact> artifacts = doResolveArtifacts(task, requests);
      telemetry.shutdown();
      return new MavenServerResponse<>(artifacts, getLongRunningTaskStatus(longRunningTaskId, token));
    }
  }

  private @NotNull ArrayList<MavenArtifact> doResolveArtifacts(@NotNull LongRunningTask task,
                                                               @NotNull Collection<MavenArtifactResolutionRequest> requests) {
    if (requests.isEmpty()) return new ArrayList<>();
    try {
      boolean updateSnapshots = myAlwaysUpdateSnapshots || requests.iterator().next().updateSnapshots();
      MavenExecutionRequest executionRequest =
        createRequest(null, null, null);
      if (!requests.isEmpty() && updateSnapshots) {
        executionRequest.setUpdateSnapshots(true);
      }
      ArrayList<MavenArtifact> artifacts = new ArrayList<>();
      Set<MavenRemoteRepository> repos = new LinkedHashSet<>();
      for (MavenArtifactResolutionRequest request : requests) {
        repos.addAll(request.getRemoteRepositories());
      }


      executeWithMavenSession(executionRequest, MavenWorkspaceMap.empty(), task.getIndicator(), mavenSession -> {
        try {
          List<ArtifactRepository> repositories = map2ArtifactRepositories(mavenSession, new ArrayList<>(repos), updateSnapshots);
          repositories.forEach(executionRequest::addRemoteRepository);

          RepositorySystem repositorySystem = getComponent(RepositorySystem.class);
          for (MavenArtifactResolutionRequest request : requests) {
            MavenArtifact artifact = tryResolveArtifact(mavenSession, request, repositorySystem, repositories);
            artifacts.add(artifact);
            task.incrementFinishedRequests();
          }
        }
        catch (Exception e) {
          throw wrapToSerializableRuntimeException(e);
        }
      });
      return artifacts;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private @NotNull MavenArtifact tryResolveArtifact(MavenSession mavenSession,
                                                    MavenArtifactResolutionRequest request,
                                                    RepositorySystem repositorySystem,
                                                    List<ArtifactRepository> repositories) {
    try {
      ArtifactResult artifactResult = repositorySystem.resolveArtifact(
        mavenSession.getRepositorySession(),
        new ArtifactRequest(RepositoryUtils.toArtifact(createArtifact(request.getArtifactInfo())),
                            RepositoryUtils.toRepos(repositories), null));
      return Maven40ModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifactResult.getArtifact()), getLocalRepositoryFile());
    }
    catch (ArtifactResolutionException e) {
      return Maven40ModelConverter.convertArtifact(createArtifact(request.getArtifactInfo()), getLocalRepositoryFile());
    }
  }


  private static void initLogging(Maven40ServerConsoleLogger consoleWrapper) {
    Maven40Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }

  private List<ArtifactRepository> map2ArtifactRepositories(@Nullable MavenSession session,
                                                            List<MavenRemoteRepository> repositories,
                                                            boolean forceResolveSnapshots) {
    List<ArtifactRepository> result = new ArrayList<>();
    for (MavenRemoteRepository each : repositories) {
      try {
        result.add(buildArtifactRepository(session, Maven40ModelConverter.toNativeRepository(each, forceResolveSnapshots)));
      }
      catch (InvalidRepositoryException e) {
        MavenServerGlobals.getLogger().warn(e);
      }
    }
    return result;
  }

  private ArtifactRepository buildArtifactRepository(@Nullable MavenSession session, Repository repo) throws InvalidRepositoryException {
    MavenRepositorySystem repositorySystem = myRepositorySystem;

    ArtifactRepository repository = MavenRepositorySystem.buildArtifactRepository(repo);

    RepositorySystemSession repositorySession = session == null ? null : session.getRepositorySession();
    if (repositorySession != null) {
      repositorySystem.injectMirror(repositorySession, Collections.singletonList(repository));
      repositorySystem.injectProxy(repositorySession, Collections.singletonList(repository));
      repositorySystem.injectAuthentication(repositorySession, Collections.singletonList(repository));
    }

    return repository;
  }

  private Artifact createArtifact(MavenArtifactInfo info) {
    return getComponent(ArtifactFactory.class)
      .createArtifactWithClassifier(info.getGroupId(), info.getArtifactId(), info.getVersion(), info.getPackaging(), info.getClassifier());
  }

  @Override
  public HashSet<MavenRemoteRepository> resolveRepositories(@NotNull ArrayList<MavenRemoteRepository> repositories, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return new HashSet<>(
        convertRemoteRepositories(map2ArtifactRepositories(null, new ArrayList<>(repositories), false)));
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  @NotNull
  public MavenServerResponse<@NotNull MavenArtifactResolveResult> resolveArtifactsTransitively(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull ArrayList<MavenArtifactInfo> artifacts,
    @NotNull ArrayList<MavenRemoteRepository> remoteRepositories,
    MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      String longRunningTaskId = longRunningTaskInput.getLongRunningTaskId();
      try (LongRunningTask task = newLongRunningTask(longRunningTaskId, artifacts.size(), myConsoleWrapper)) {
        MavenArtifactResolveResult result = resolveArtifactsTransitively(task ,artifacts);
        return new MavenServerResponse<>(result, getLongRunningTaskStatus(longRunningTaskId, token));
      }
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  private MavenArtifactResolveResult resolveArtifactsTransitively(@NotNull LongRunningTask task, @NotNull ArrayList<MavenArtifactInfo> artifacts) {
    if (artifacts.isEmpty()) return new MavenArtifactResolveResult(new ArrayList<>(), null);
    try {
      return doResolveArtifactsTransitively(task, artifacts);
    }
    catch (Throwable e) {
      MavenServerGlobals.getLogger().error(e);
      Artifact transferArtifact = getProblemTransferArtifact(e);
      String message = getRootMessage(e);
      MavenProjectProblem problem;
      if (transferArtifact != null) {
        MavenArtifact mavenArtifact = Maven40ModelConverter.convertArtifact(transferArtifact, getLocalRepositoryFile());
        problem = MavenProjectProblem.createRepositoryProblem("", message, false, mavenArtifact);
      }
      else {
        problem = MavenProjectProblem.createStructureProblem("", message);
      }
      return new MavenArtifactResolveResult(Collections.emptyList(), problem);
    }
  }

  private MavenArtifactResolveResult doResolveArtifactsTransitively(LongRunningTask task, @NotNull List<MavenArtifactInfo> artifacts) {
    MavenExecutionRequest request = createRequest(null, null, null);

    Map<DownloadedArtifact, Path> resolvedArtifactMap = new HashMap<>();

    executeWithMavenSession(request, MavenWorkspaceMap.empty(), task.getIndicator(), mavenSession -> {
      Session session = mavenSession.getSession();
      for (MavenArtifactInfo mavenArtifactInfo : artifacts) {
        if (task.isCanceled()) break;
        ArtifactCoordinates coordinate = session.createArtifactCoordinates(
          mavenArtifactInfo.getGroupId(),
          mavenArtifactInfo.getArtifactId(),
          mavenArtifactInfo.getVersion(),
          mavenArtifactInfo.getClassifier(),
          mavenArtifactInfo.getPackaging(),
          null);

        ArtifactResolver artifactResolver = session.getService(ArtifactResolver.class);
        ArtifactResolverResult resolved = artifactResolver.resolve(session, Collections.singleton(coordinate));
        resolved.getArtifacts().forEach(a -> {
          resolvedArtifactMap.put(a, a.getPath());
        });

        DependencyCoordinates dependencyCoordinate = session.createDependencyCoordinates(coordinate);

        // TODO: what's the correct PathScope here?
        Node dependencyNode = session.collectDependencies(dependencyCoordinate, PathScope.MAIN_COMPILE);

        List<DependencyCoordinates> dependencyCoordinates = dependencyNode.stream()
          .filter(node -> node != dependencyNode)
          .filter(node -> node.getDependency() != null)
          .map(node -> node.getDependency().toCoordinates())
          .filter(distinctByKey(
            coords ->
              Arrays.asList(
                coords.getGroupId(),
                coords.getArtifactId(),
                coords.getVersionConstraint().toString(),
                coords.getClassifier(),
                coords.getExtension(),
                coords.getOptional()
              )))
          .collect(Collectors.toList());
        ArtifactResolverResult resolvedChildren = artifactResolver.resolve(session, dependencyCoordinates);

        resolvedChildren.getArtifacts().forEach(a -> {
          resolvedArtifactMap.put(a, a.getPath());
        });
        task.incrementFinishedRequests();
      }
    });


    File localRepositoryFile = getLocalRepositoryFile();
    List<MavenArtifact> resolvedArtifacts = new ArrayList<>();
    for (DownloadedArtifact apiArtifact : resolvedArtifactMap.keySet()) {
      Path artifactPath = resolvedArtifactMap.get(apiArtifact);
      MavenArtifact mavenArtifact = Maven40ApiModelConverter.convertArtifactAndPath(apiArtifact, artifactPath, localRepositoryFile);
      resolvedArtifacts.add(mavenArtifact);
    }

    return new MavenArtifactResolveResult(resolvedArtifacts, null);
  }

  private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  @Override
  public ArrayList<MavenArchetype> getLocalArchetypes(MavenToken token, @NotNull String path) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Override
  public ArrayList<MavenArchetype> getRemoteArchetypes(MavenToken token, @NotNull String url) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable HashMap<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId,
                                                                            @NotNull String version,
                                                                            @NotNull ArrayList<MavenRemoteRepository> repositories,
                                                                            @Nullable String url, MavenToken token) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }


  private void customizeComponents(@Nullable MavenWorkspaceMap workspaceMap) {
    //noinspection EmptyTryBlock
    try {
      // TODO: implement
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private void resetComponents() {
    //noinspection EmptyTryBlock
    try {
      // TODO: implement
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Nonnull
  public static Path getCanonicalPath(Path path) {
    requireNonNull(path, "path");
    try {
      return path.toRealPath();
    }
    catch (IOException e) {
      return getCanonicalPath(path.getParent()).resolve(path.getFileName());
    }
  }
}
