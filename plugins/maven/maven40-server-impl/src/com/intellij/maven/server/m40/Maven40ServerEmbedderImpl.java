// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40;

import com.intellij.maven.server.m40.utils.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtilRt;
import org.apache.commons.cli.ParseException;
import org.apache.maven.*;
import org.apache.maven.api.ArtifactCoordinate;
import org.apache.maven.api.DependencyCoordinate;
import org.apache.maven.api.Node;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ArtifactResolver;
import org.apache.maven.api.services.ArtifactResolverResult;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.execution.*;
import org.apache.maven.internal.impl.DefaultSession;
import org.apache.maven.internal.impl.DefaultSessionFactory;
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
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.maven.server.m40.utils.Maven40ModelConverter.convertRemoteRepositories;

public class Maven40ServerEmbedderImpl extends MavenServerEmbeddedBase {
  @NotNull private final DefaultPlexusContainer myContainer;
  @NotNull private final Settings myMavenSettings;

  private final Maven40ServerConsoleLogger myConsoleWrapper;

  private final Properties mySystemProperties;

  private final boolean myAlwaysUpdateSnapshots;

  @NotNull private final MavenRepositorySystem myRepositorySystem;

  @NotNull private final Maven40ImporterSpy myImporterSpy;

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
      List<String> commandLineOptions = createCommandLineOptions(serverSettings);
      myAlwaysUpdateSnapshots = commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots");

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

    myRepositorySystem = getComponent(MavenRepositorySystem.class);

    Maven40ImporterSpy importerSpy = getComponentIfExists(Maven40ImporterSpy.class);

    if (importerSpy == null) {
      importerSpy = new Maven40ImporterSpy();
      myContainer.addComponent(importerSpy, Maven40ImporterSpy.class.getName());
    }
    myImporterSpy = importerSpy;
  }

  @NotNull
  private static List<String> createCommandLineOptions(MavenServerSettings serverSettings) {
    List<String> commandLineOptions = new ArrayList<String>(serverSettings.getUserProperties().size());
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

    String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
    if (mavenEmbedderCliOptions != null) {
      commandLineOptions.addAll(StringUtilRt.splitHonorQuotes(mavenEmbedderCliOptions, ' '));
    }

    String globalSettingsPath = serverSettings.getGlobalSettingsPath();
    if (globalSettingsPath != null && new File(globalSettingsPath).isFile()) {
      commandLineOptions.add("-gs");
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

    return commandLineOptions;
  }
  @NotNull
  @Override
  public MavenServerResponse<ArrayList<MavenServerExecutionResult>> resolveProjects(@NotNull String longRunningTaskId,
                                                                                    @NotNull ProjectResolutionRequest request,
                                                                                    MavenToken token) {
    MavenServerUtil.checkToken(token);
    Map<@NotNull File, String> fileToChecksum = request.getFileToChecksum();
    List<String> activeProfiles = request.getActiveProfiles();
    List<String> inactiveProfiles = request.getInactiveProfiles();
    MavenWorkspaceMap workspaceMap = request.getWorkspaceMap();
    boolean updateSnapshots = myAlwaysUpdateSnapshots || request.updateSnapshots();
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, fileToChecksum.size(), myConsoleWrapper)) {
      Maven40ProjectResolver projectResolver = new Maven40ProjectResolver(
        this,
        updateSnapshots,
        myImporterSpy,
        task.getIndicator(),
        workspaceMap,
        getLocalRepositoryFile(),
        request.getUserProperties(),
        canResolveDependenciesInParallel()
      );
      try {
        customizeComponents(workspaceMap);
        ArrayList<MavenServerExecutionResult> result = projectResolver.resolveProjects(
          task,
          fileToChecksum,
          activeProfiles,
          inactiveProfiles);
        return new MavenServerResponse(result, getLongRunningTaskStatus(longRunningTaskId, token));
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

  public void collectProblems(@Nullable File file,
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
  public static String getRootMessage(Throwable each) {
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

  @SuppressWarnings({"unchecked"})
  private <T> T getComponent(Class<T> clazz, String roleHint) {
    try {
      return (T)myContainer.lookup(clazz.getName(), roleHint);
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
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

  private static MavenId extractIdFromException(Throwable exception) {
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

  public MavenExecutionRequest createRequest(File file,
                                             List<String> activeProfiles,
                                             List<String> inactiveProfiles) {
    return createRequest(file, activeProfiles, inactiveProfiles, new Properties());
  }

  public MavenExecutionRequest createRequest(@Nullable File file,
                                             @Nullable List<String> activeProfiles,
                                             @Nullable List<String> inactiveProfiles,
                                             @NotNull Properties customProperties) {

    MavenExecutionRequest result = new DefaultMavenExecutionRequest();

    try {
      injectDefaultRepositories(result);
      injectDefaultPluginRepositories(result);

      getComponent(MavenExecutionRequestPopulator.class).populateFromSettings(result, myMavenSettings);

      result.setPom(file);

      getComponent(MavenExecutionRequestPopulator.class).populateDefaults(result);

      result.setSystemProperties(mySystemProperties);
      Properties userProperties = new Properties();
      if (file != null) {
        userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigPropertiesForNestedProjectDir(file.getParentFile()));
      }
      userProperties.putAll(customProperties);
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

  private void injectDefaultRepositories(MavenExecutionRequest request)
    throws MavenExecutionRequestPopulationException {
    Set<String> definedRepositories = myRepositorySystem.getRepoIds(request.getRemoteRepositories());

    if (!definedRepositories.contains(MavenRepositorySystem.DEFAULT_REMOTE_REPO_ID)) {
      try {
        request.addRemoteRepository(myRepositorySystem.createDefaultRemoteRepository(request));
      } catch (Exception e) {
        throw new MavenExecutionRequestPopulationException("Cannot create default remote repository.", e);
      }
    }
  }

  private void injectDefaultPluginRepositories(MavenExecutionRequest request)
    throws MavenExecutionRequestPopulationException {
    Set<String> definedRepositories = myRepositorySystem.getRepoIds(request.getPluginArtifactRepositories());

    if (!definedRepositories.contains(MavenRepositorySystem.DEFAULT_REMOTE_REPO_ID)) {
      try {
        request.addPluginArtifactRepository(myRepositorySystem.createDefaultRemoteRepository(request));
      } catch (Exception e) {
        throw new MavenExecutionRequestPopulationException("Cannot create default remote repository.", e);
      }
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
   * adapted from {@link DefaultMaven#getLifecycleParticipants(Collection)}
   */
  public Collection<AbstractMavenLifecycleParticipant> getLifecycleParticipants(Collection<MavenProject> projects) {
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


  @Override
  public MavenServerResponse<ArrayList<PluginResolutionResponse>> resolvePlugins(@NotNull String longRunningTaskId,
                                                                                 @NotNull ArrayList<PluginResolutionRequest> pluginResolutionRequests,
                                                                                 boolean forceUpdateSnapshots,
                                                                                 MavenToken token) {
    MavenServerUtil.checkToken(token);

    boolean runInParallel = canResolveDependenciesInParallel();
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, pluginResolutionRequests.size(), myConsoleWrapper)) {
      MavenExecutionRequest request = createRequest(null, null, null);
      request.setTransferListener(new Maven40TransferListenerAdapter(task.getIndicator()));
      request.setUpdateSnapshots(myAlwaysUpdateSnapshots || forceUpdateSnapshots);

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession session = maven.newRepositorySession(request);
      myImporterSpy.setIndicator(task.getIndicator());

      List<PluginResolutionData> resolutions = new ArrayList<>();

      for (PluginResolutionRequest pluginResolutionRequest : pluginResolutionRequests) {
        MavenId mavenPluginId = pluginResolutionRequest.getMavenPluginId();
        int nativeMavenProjectId = pluginResolutionRequest.getNativeMavenProjectId();

        String groupId = mavenPluginId.getGroupId();
        String artifactId = mavenPluginId.getArtifactId();

        MavenProject project = RemoteNativeMaven40ProjectHolder.findProjectById(nativeMavenProjectId);
        List<RemoteRepository> remoteRepos = project.getRemotePluginRepositories();

        Plugin pluginFromProject = project.getBuild().getPluginsAsMap().get(groupId + ':' + artifactId);
        List<Dependency> pluginDependencies =
          null == pluginFromProject ? Collections.emptyList() : pluginFromProject.getDependencies();

        PluginResolutionData resolution = new PluginResolutionData(mavenPluginId, pluginDependencies, remoteRepos);
        resolutions.add(resolution);
      }
      // IDEA-341451: Parallel plugin resolution hangs in Maven 4.0.0-alpha-9
      // It worked fine up until Maven 4.0.0-alpha-8
      List<PluginResolutionResponse> results = ParallelRunnerForServer.execute(false, resolutions, resolution ->
        resolvePlugin(task, resolution.mavenPluginId, resolution.pluginDependencies, resolution.remoteRepos, session)
      );
      return new MavenServerResponse<>(new ArrayList<>(results), getLongRunningTaskStatus(longRunningTaskId, token));
    }
  }

  private static class PluginResolutionData {
    MavenId mavenPluginId;
    List<Dependency> pluginDependencies;
    List<RemoteRepository> remoteRepos;

    private PluginResolutionData(MavenId mavenPluginId,
                                 List<Dependency> pluginDependencies,
                                 List<RemoteRepository> remoteRepos) {
      this.mavenPluginId = mavenPluginId;
      this.pluginDependencies = pluginDependencies;
      this.remoteRepos = remoteRepos;
    }
  }

  @NotNull
  private PluginResolutionResponse resolvePlugin(LongRunningTask task,
                                                 MavenId mavenPluginId,
                                                 List<Dependency> pluginDependencies,
                                                 List<RemoteRepository> remoteRepos,
                                                 RepositorySystemSession session) {
    long startTime = System.currentTimeMillis();
    List<MavenArtifact> artifacts = new ArrayList<>();
    if (task.isCanceled()) return new PluginResolutionResponse(mavenPluginId, false, artifacts);

    try {
      Plugin plugin = new Plugin();
      plugin.setGroupId(mavenPluginId.getGroupId());
      plugin.setArtifactId(mavenPluginId.getArtifactId());
      plugin.setVersion(mavenPluginId.getVersion());
      plugin.setDependencies(pluginDependencies);

      PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);

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

      task.incrementFinishedRequests();
      return new PluginResolutionResponse(mavenPluginId, true, artifacts);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn(e);
      return new PluginResolutionResponse(mavenPluginId, false, artifacts);
    } finally {
      long totalTime = System.currentTimeMillis() - startTime;
      MavenServerGlobals.getLogger().debug("Resolved plugin " + mavenPluginId + " in " + totalTime + " ms");
    }
  }


  @Nullable
  @Override
  public String evaluateEffectivePom(@NotNull File file,
                                     @NotNull ArrayList<String> activeProfiles,
                                     @NotNull ArrayList<String> inactiveProfiles,
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
  public MavenServerResponse<ArrayList<MavenGoalExecutionResult>> executeGoal(@NotNull String longRunningTaskId,
                                                                              @NotNull ArrayList<MavenGoalExecutionRequest> requests,
                                                                              @NotNull String goal,
                                                                              MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      return new MavenServerResponse<>(executeGoal(task, requests, goal), getLongRunningTaskStatus(longRunningTaskId, token));
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
  public MavenServerResponse<ArrayList<MavenArtifact>> resolveArtifacts(@NotNull String longRunningTaskId,
                                                                        @NotNull ArrayList<MavenArtifactResolutionRequest> requests,
                                                                        MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      return new MavenServerResponse<>(doResolveArtifacts(task, requests), getLongRunningTaskStatus(longRunningTaskId, token));
    }
  }

  @NotNull
  private ArrayList<MavenArtifact> doResolveArtifacts(@NotNull LongRunningTask task,
                                                 @NotNull Collection<MavenArtifactResolutionRequest> requests) {
    try {
      ArrayList<MavenArtifact> artifacts = new ArrayList<>();
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

  private static void initLogging(Maven40ServerConsoleLogger consoleWrapper) {
    Maven40Sl4jLoggerWrapper.setCurrentWrapper(consoleWrapper);
  }

  @NotNull
  private List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) {
    List<ArtifactRepository> result = map2ArtifactRepositories(repositories);
    if (getComponent(LegacySupport.class).getRepositorySession() == null) {
      myRepositorySystem.injectMirror(result, myMavenSettings.getMirrors());
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
    MavenRepositorySystem repositorySystem = myRepositorySystem;
    RepositorySystemSession session = getComponent(LegacySupport.class).getRepositorySession();

    ArtifactRepository repository = MavenRepositorySystem.buildArtifactRepository(repo);

    if (session != null) {
      repositorySystem.injectMirror(session, Collections.singletonList(repository));
      repositorySystem.injectProxy(session, Collections.singletonList(repository));
      repositorySystem.injectAuthentication(session, Collections.singletonList(repository));
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
        convertRemoteRepositories(convertRepositories(new ArrayList<>(repositories))));
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }


  @NotNull
  @Override
  public MavenArtifactResolveResult resolveArtifactsTransitively(
    @NotNull ArrayList<MavenArtifactInfo> artifacts,
    @NotNull ArrayList<MavenRemoteRepository> remoteRepositories,
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
  public ArrayList<MavenArchetype> getLocalArchetypes(MavenToken token, @NotNull String path) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Override
  public ArrayList<MavenArchetype> getRemoteArchetypes(MavenToken token, @NotNull String url) {
    MavenServerUtil.checkToken(token);
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public HashMap<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId,
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

}
