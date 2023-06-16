// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtilRt;
import com.intellij.util.containers.ContainerUtilRt;
import org.apache.commons.cli.ParseException;
import org.apache.maven.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.execution.*;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.apache.maven.model.path.UrlNormalizer;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.PluginDescriptorCache;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.profiles.activation.JdkPrefixProfileActivator;
import org.apache.maven.profiles.activation.OperatingSystemProfileActivator;
import org.apache.maven.profiles.activation.ProfileActivator;
import org.apache.maven.profiles.activation.SystemPropertyProfileActivator;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.project.*;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.logging.BaseLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.embedder.*;
import org.jetbrains.idea.maven.server.security.MavenToken;
import org.jetbrains.idea.maven.server.utils.Maven3SettingsBuilder;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.LocalRepositoryManager;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Overridden maven components:
 * <p/>
 * maven-compat:
 * org.jetbrains.idea.maven.server.embedder.CustomMaven3RepositoryMetadataManager <-> org.apache.maven.artifact.repository.metadata.DefaultRepositoryMetadataManager
 * org.jetbrains.idea.maven.server.embedder.CustomMaven30ArtifactResolver <-> org.apache.maven.artifact.resolver.DefaultArtifactResolver
 * org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator <-> org.apache.maven.project.interpolation.StringSearchModelInterpolator
 * <p/>
 * maven-core:
 * org.jetbrains.idea.maven.server.embedder.CustomMaven3ArtifactFactory <-> org.apache.maven.artifact.factory.DefaultArtifactFactory
 * org.jetbrains.idea.maven.server.embedder.CustomPluginDescriptorCache <-> org.apache.maven.plugin.DefaultPluginDescriptorCache
 * <p/>
 * maven-model-builder:
 * org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2 <-> org.apache.maven.model.interpolation.StringSearchModelInterpolator
 * org.jetbrains.idea.maven.server.embedder.CustomModelValidator <-> org.apache.maven.model.validation.ModelValidator
 */
public class Maven30ServerEmbedderImpl extends Maven3ServerEmbedder {

  @NotNull private final DefaultPlexusContainer myContainer;
  @NotNull private final Settings myMavenSettings;

  private final ArtifactRepository myLocalRepository;
  private final Maven3ServerConsoleLogger myConsoleWrapper;

  private final Properties mySystemProperties;

  private final boolean myAlwaysUpdateSnapshots;

  @NotNull private final RepositorySystem myRepositorySystem;

  public Maven30ServerEmbedderImpl(MavenServerSettings settings) {
    super(settings);

    String mavenHomePath = settings.getMavenHomePath();
    if (mavenHomePath != null) {
      System.setProperty("maven.home", mavenHomePath);
    }

    myConsoleWrapper = new Maven3ServerConsoleLogger();
    myConsoleWrapper.setThreshold(settings.getLoggingLevel());

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

    Class<?> cliRequestClass;
    try {
      cliRequestClass = MavenCli.class.getClassLoader().loadClass("org.apache.maven.cli.MavenCli$CliRequest");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("Class \"org.apache.maven.cli.MavenCli$CliRequest\" not found");
    }

    Object cliRequest;
    try {
      List<String> commandLineOptions = new ArrayList<String>(settings.getUserProperties().size());
      for (Map.Entry<Object, Object> each : settings.getUserProperties().entrySet()) {
        commandLineOptions.add("-D" + each.getKey() + "=" + each.getValue());
      }

      if (settings.getLoggingLevel() == MavenServerConsoleIndicator.LEVEL_DEBUG) {
        commandLineOptions.add("-X");
        commandLineOptions.add("-e");
      }
      else if (settings.getLoggingLevel() == MavenServerConsoleIndicator.LEVEL_DISABLED) {
        commandLineOptions.add("-q");
      }

      String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
      if (mavenEmbedderCliOptions != null) {
        commandLineOptions.addAll(StringUtilRt.splitHonorQuotes(mavenEmbedderCliOptions, ' '));
      }
      myAlwaysUpdateSnapshots = commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots");

      Constructor<?> constructor = cliRequestClass.getDeclaredConstructor(String[].class, ClassWorld.class);
      constructor.setAccessible(true);
      //noinspection SSBasedInspection
      cliRequest = constructor.newInstance(commandLineOptions.toArray(new String[0]), classWorld);

      for (String each : new String[]{"initialize", "cli", "logging", "properties", "container"}) {
        Method m = MavenCli.class.getDeclaredMethod(each, cliRequestClass);
        m.setAccessible(true);
        m.invoke(cli, cliRequest);
      }
    }

    catch (InstantiationException | NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      ParseException cause = ExceptionUtilRt.findCause(e, ParseException.class);
      if (cause != null) {
        String workingDir = System.getProperty("user.dir");
        throw new MavenConfigParseException(cause.getMessage(), workingDir);
      }
      throw new RuntimeException(e);
    }

    // reset threshold
    myContainer = ReflectionUtilRt.getField(MavenCli.class, cli, DefaultPlexusContainer.class, "container");
    myContainer.getLoggerManager().setThreshold(settings.getLoggingLevel());

    mySystemProperties = ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "systemProperties");

    if (settings.getProjectJdk() != null) {
      mySystemProperties.setProperty("java.home", settings.getProjectJdk());
    }

    myMavenSettings = Maven3SettingsBuilder.buildSettings(
      ReflectionUtilRt.getField(MavenCli.class, cli, SettingsBuilder.class, "settingsBuilder"),
      settings,
      mySystemProperties,
      ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "userProperties")
    );

    myLocalRepository = createLocalRepository();

    myRepositorySystem = getComponent(RepositorySystem.class);
  }

  @Override
  @NotNull
  protected PlexusContainer getContainer() {
    return myContainer;
  }

  private static Maven3ExecutionResult handleException(Throwable e) {
    if (e instanceof Error) throw (Error)e;

    return new Maven3ExecutionResult(Collections.singletonList((Exception)e));
  }

  private static Collection<String> collectActivatedProfiles(MavenProject mavenProject) {
    // for some reason project's active profiles do not contain parent's profiles - only local and settings'.
    // parent's profiles do not contain settings' profiles.

    List<Profile> profiles = new ArrayList<Profile>();
    try {
      while (mavenProject != null) {
        profiles.addAll(mavenProject.getActiveProfiles());
        mavenProject = mavenProject.getParent();
      }
    }
    catch (Exception e) {
      // don't bother user if maven failed to build parent project
      MavenServerGlobals.getLogger().info(e);
    }
    return collectProfilesIds(profiles);
  }

  private static List<Exception> filterExceptions(List<Throwable> list) {
    for (Throwable throwable : list) {
      if (!(throwable instanceof Exception)) {
        throw new RuntimeException(throwable);
      }
    }

    return (List<Exception>)((List)list);
  }

  @NotNull
  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    Model result = Maven3ModelConverter.toNativeModel(model);
    result = doInterpolate(result, basedir);

    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(result, basedir);

    return Maven3ModelConverter.convertModel(result, null);
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    Model result = Maven3ModelConverter.toNativeModel(model);
    new DefaultModelInheritanceAssembler().assembleModelInheritance(result, Maven3ModelConverter.toNativeModel(parentModel));
    return Maven3ModelConverter.convertModel(result, null);
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       MavenExplicitProfiles explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) {
    Model nativeModel = Maven3ModelConverter.toNativeModel(model);

    Collection<String> enabledProfiles = explicitProfiles.getEnabledProfiles();
    Collection<String> disabledProfiles = explicitProfiles.getDisabledProfiles();
    List<Profile> activatedPom = new ArrayList<Profile>();
    List<Profile> activatedExternal = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    List<Profile> rawProfiles = nativeModel.getProfiles();
    List<Profile> expandedProfilesCache = null;
    List<Profile> deactivatedProfiles = new ArrayList<Profile>();

    for (int i = 0; i < rawProfiles.size(); i++) {
      Profile eachRawProfile = rawProfiles.get(i);

      if (disabledProfiles.contains(eachRawProfile.getId())) {
        deactivatedProfiles.add(eachRawProfile);
        continue;
      }

      boolean shouldAdd = enabledProfiles.contains(eachRawProfile.getId()) || alwaysOnProfiles.contains(eachRawProfile.getId());

      Activation activation = eachRawProfile.getActivation();
      if (activation != null) {
        if (activation.isActiveByDefault()) {
          activeByDefault.add(eachRawProfile);
        }

        // expand only if necessary
        if (expandedProfilesCache == null) expandedProfilesCache = doInterpolate(nativeModel, basedir).getProfiles();
        Profile eachExpandedProfile = expandedProfilesCache.get(i);

        for (ProfileActivator eachActivator : getProfileActivators(basedir)) {
          try {
            if (eachActivator.canDetermineActivation(eachExpandedProfile) && eachActivator.isActive(eachExpandedProfile)) {
              shouldAdd = true;
              break;
            }
          }
          catch (Exception e) {
            MavenServerGlobals.getLogger().warn(e);
          }
        }
      }

      if (shouldAdd) {
        if (MavenConstants.PROFILE_FROM_POM.equals(eachRawProfile.getSource())) {
          activatedPom.add(eachRawProfile);
        }
        else {
          activatedExternal.add(eachRawProfile);
        }
      }
    }

    List<Profile> activatedProfiles = new ArrayList<Profile>(activatedPom.isEmpty() ? activeByDefault : activatedPom);
    activatedProfiles.addAll(activatedExternal);

    for (Profile each : activatedProfiles) {
      new DefaultProfileInjector().injectProfile(nativeModel, each, null, null);
    }

    return new ProfileApplicationResult(Maven3ModelConverter.convertModel(nativeModel, null),
                                        new MavenExplicitProfiles(collectProfilesIds(activatedProfiles),
                                                                  collectProfilesIds(deactivatedProfiles))
    );
  }

  private static Model doInterpolate(Model result, File basedir) {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomMaven3ModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenServerUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
      config.setBuildStartTime(new Date());

      Properties userProperties = new Properties();
      userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigProperties(basedir));
      config.setUserProperties(userProperties);

      result = interpolator.interpolate(result, basedir, config, false);
    }
    catch (ModelInterpolationException e) {
      MavenServerGlobals.getLogger().warn(e);
    }
    catch (InitializationException e) {
      MavenServerGlobals.getLogger().error(e);
    }
    return result;
  }

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Collection<String> result = new HashSet<String>();
    for (Profile each : profiles) {
      if (each.getId() != null) {
        result.add(each.getId());
      }
    }
    return result;
  }

  private static ProfileActivator[] getProfileActivators(File basedir) {
    SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenServerUtil.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      MavenServerGlobals.getLogger().error(e);
      return new ProfileActivator[0];
    }

    return new ProfileActivator[]{new MyFileProfileActivator(basedir), sysPropertyActivator, new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz, String roleHint) {
    try {
      return (T)myContainer.lookup(clazz.getName(), roleHint);
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz) {
    try {
      return (T)myContainer.lookup(clazz.getName());
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  private ArtifactRepository createLocalRepository() {
    try {
      final ArtifactRepository localRepository =
        getComponent(RepositorySystem.class).createLocalRepository(new File(myMavenSettings.getLocalRepository()));
      final String customRepoId = System.getProperty("maven3.localRepository.id", "localIntelliJ");
      if (customRepoId != null) {
        // see details at https://youtrack.jetbrains.com/issue/IDEA-121292
        localRepository.setId(customRepoId);
      }
      return localRepository;
    }
    catch (InvalidRepositoryException e) {
      throw new RuntimeException(e);
      // Legacy code.
    }
  }

  private void customizeComponents(@Nullable MavenWorkspaceMap workspaceMap) {
    try {
      // replace some plexus components
      myContainer.addComponent(getComponent(ArtifactFactory.class, "ide"), ArtifactFactory.ROLE);
      myContainer.addComponent(getComponent(ArtifactResolver.class, "ide"), ArtifactResolver.ROLE);
      myContainer.addComponent(getComponent(RepositoryMetadataManager.class, "ide"), RepositoryMetadataManager.class.getName());
      myContainer.addComponent(getComponent(PluginDescriptorCache.class, "ide"), PluginDescriptorCache.class.getName());
      ModelInterpolator modelInterpolator = getComponent(ModelInterpolator.class, "ide");
      myContainer.addComponent(modelInterpolator, ModelInterpolator.class.getName());
      myContainer.addComponent(getComponent(org.apache.maven.project.interpolation.ModelInterpolator.class, "ide"),
                               org.apache.maven.project.interpolation.ModelInterpolator.ROLE);
      ModelValidator modelValidator = getComponent(ModelValidator.class, "ide");
      myContainer.addComponent(modelValidator, ModelValidator.class.getName());

      DefaultModelBuilder defaultModelBuilder = (DefaultModelBuilder)getComponent(ModelBuilder.class);
      defaultModelBuilder.setModelValidator(modelValidator);
      defaultModelBuilder.setModelInterpolator(modelInterpolator);

      // IDEA-176117 - in older version of maven (3.0 - 3.0.3) "modelInterpolator" component is not fully initialized for some reason
      // - "pathTranslator" and "urlNormalizer" fields are null, which causes NPE in paths interpolation process.
      // Latest version (3.0.5) is the stable one so we expect "modelInterpolator" to be already valid in it.
      if (!"3.0.5".equals(getMavenVersion()) && modelInterpolator instanceof StringSearchModelInterpolator) {
        ((StringSearchModelInterpolator)modelInterpolator).setPathTranslator(getComponent(org.apache.maven.model.path.PathTranslator.class));
        ((StringSearchModelInterpolator)modelInterpolator).setUrlNormalizer(getComponent(UrlNormalizer.class));
      }

      ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
      if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
        ((CustomMaven3ArtifactFactory)artifactFactory).customize();
      }
      ((CustomMaven30ArtifactResolver)getComponent(ArtifactResolver.class)).customize(workspaceMap, false);
      ((CustomMaven3RepositoryMetadataManager)getComponent(RepositoryMetadataManager.class)).customize(workspaceMap);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private void resetComponents() {
    try {
      ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
      if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
        ((CustomMaven3ArtifactFactory)artifactFactory).reset();
      }
      ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
      if (artifactResolver instanceof CustomMaven30ArtifactResolver) {
        ((CustomMaven30ArtifactResolver)artifactResolver).reset();
      }
      RepositoryMetadataManager repositoryMetadataManager = getComponent(RepositoryMetadataManager.class);
      if (repositoryMetadataManager instanceof CustomMaven3RepositoryMetadataManager) {
        ((CustomMaven3RepositoryMetadataManager)repositoryMetadataManager).reset();
      }
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Nullable
  @Override
  public String evaluateEffectivePom(@NotNull File file,
                                     @NotNull List<String> activeProfiles,
                                     @NotNull List<String> inactiveProfiles,
                                     MavenToken token) {
    MavenServerUtil.checkToken(token);
    return Maven3EffectivePomDumper.evaluateEffectivePom(this, file, activeProfiles, inactiveProfiles);
  }

  @Override
  public void executeWithMavenSession(MavenExecutionRequest request, Runnable runnable) {
    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    RepositorySystemSession repositorySession = maven.newRepositorySession(request);

    request.getProjectBuildingRequest().setRepositorySession(repositorySession);

    MavenSession mavenSession = new MavenSession(myContainer, repositorySession, request, new DefaultMavenExecutionResult());
    LegacySupport legacySupport = getComponent(LegacySupport.class);

    MavenSession oldSession = legacySupport.getSession();

    legacySupport.setSession(mavenSession);

    // adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
    try {
      for (AbstractMavenLifecycleParticipant listener : getLifecycleParticipants(Collections.emptyList())) {
        listener.afterSessionStart(mavenSession);
      }
    }
    catch (MavenExecutionException e) {
      throw new RuntimeException(e);
    }

    try {
      runnable.run();
    }
    finally {
      legacySupport.setSession(oldSession);
    }
  }

  @NotNull
  @Override
  public Collection<MavenServerExecutionResult> resolveProjects(@NotNull String longRunningTaskId,
                                                                @NotNull ProjectResolutionRequest request, MavenToken token) {
    MavenServerUtil.checkToken(token);
    List<File> files = request.getPomFiles();
    List<String> activeProfiles = request.getActiveProfiles();
    List<String> inactiveProfiles = request.getInactiveProfiles();
    MavenWorkspaceMap workspaceMap = request.getWorkspaceMap();
    boolean updateSnapshots = myAlwaysUpdateSnapshots || request.updateSnapshots();
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, files.size(), myConsoleWrapper)) {
      try {
        customizeComponents(workspaceMap);
        return resolveProjects(task, files, activeProfiles, inactiveProfiles, workspaceMap, updateSnapshots);
      }
      finally {
        resetComponents();
      }
    }
  }

  @NotNull
  private Collection<MavenServerExecutionResult> resolveProjects(@NotNull LongRunningTask task,
                                                                 @NotNull Collection<File> files,
                                                                 @NotNull List<String> activeProfiles,
                                                                 @NotNull List<String> inactiveProfiles,
                                                                 @Nullable MavenWorkspaceMap workspaceMap,
                                                                 boolean updateSnapshots) {
    DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);

    Collection<Maven3ExecutionResult> results = doResolveProject(
      task,
      files,
      activeProfiles,
      inactiveProfiles,
      Collections.singletonList(listener),
      workspaceMap,
      updateSnapshots);

    return ContainerUtilRt.map2List(results, result -> createExecutionResult(result.getPomFile(), result, listener.getRootNode()));
  }

  @NotNull
  private Collection<Maven3ExecutionResult> doResolveProject(@NotNull LongRunningTask task,
                                                             @NotNull Collection<File> files,
                                                             @NotNull List<String> activeProfiles,
                                                             @NotNull List<String> inactiveProfiles,
                                                             List<ResolutionListener> listeners,
                                                             @Nullable MavenWorkspaceMap workspaceMap,
                                                             boolean updateSnapshots) {
    File file = files.size() == 1 ? files.iterator().next() : null;
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles);

    request.setUpdateSnapshots(updateSnapshots);

    Collection<Maven3ExecutionResult> executionResults = new ArrayList<>();

    executeWithMavenSession(request, () -> {
      try {
        RepositorySystemSession repositorySession = getComponent(LegacySupport.class).getRepositorySession();
        if (repositorySession instanceof DefaultRepositorySystemSession) {
          ((DefaultRepositorySystemSession)repositorySession)
            .setTransferListener(new Maven30TransferListenerAdapter(task.getIndicator()));

          if (workspaceMap != null) {
            ((DefaultRepositorySystemSession)repositorySession).setWorkspaceReader(new Maven30WorkspaceReader(workspaceMap));
          }
        }

        List<ProjectBuildingResult> buildingResults = getProjectBuildingResults(request, files);
        task.updateTotalRequests(buildingResults.size());

        for (ProjectBuildingResult buildingResult : buildingResults) {
          if (task.isCanceled()) break;

          MavenProject project = buildingResult.getProject();

          if (project == null) {
            List<Exception> exceptions = new ArrayList<>();
            for (ModelProblem problem : buildingResult.getProblems()) {
              exceptions.add(problem.getException());
            }
            Maven3ExecutionResult mavenExecutionResult = new Maven3ExecutionResult(buildingResult.getPomFile(), exceptions);
            executionResults.add(mavenExecutionResult);
            continue;
          }

          List<Exception> exceptions = new ArrayList<>();
          loadExtensions(project, exceptions);

          //Artifact projectArtifact = project.getArtifact();
          //Map managedVersions = project.getManagedVersionMap();
          //ArtifactMetadataSource metadataSource = getComponent(ArtifactMetadataSource.class);
          project.setDependencyArtifacts(project.createArtifacts(getComponent(ArtifactFactory.class), null, null));
          //

          if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING) {
            addMvn2CompatResults(project, exceptions, listeners, myLocalRepository, executionResults);
          }
          else {
            DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
            List<Dependency> dependencies = dependencyResolutionResult.getDependencies();

            Set<Artifact> artifacts = new LinkedHashSet<>(dependencies.size());
            for (Dependency dependency : dependencies) {
              Artifact artifact = RepositoryUtils.toArtifact(dependency.getArtifact());
              artifact.setScope(dependency.getScope());
              artifact.setOptional(dependency.isOptional());
              artifacts.add(artifact);
              resolveAsModule(artifact, workspaceMap);
            }

            project.setArtifacts(artifacts);
            executionResults.add(new Maven3ExecutionResult(project, dependencyResolutionResult, exceptions, buildingResult.getProblems()));
          }

          task.incrementFinishedRequests();
        }
      }
      catch (Exception e) {
        executionResults.add(handleException(e));
      }
    });

    return executionResults;
  }

  private boolean resolveAsModule(Artifact a, @Nullable MavenWorkspaceMap workspaceMap) {
    if (workspaceMap == null) return false;

    MavenWorkspaceMap.Data resolved = workspaceMap.findFileAndOriginalId(Maven3ModelConverter.createMavenId(a));
    if (resolved == null) return false;

    a.setResolved(true);
    a.setFile(resolved.getFile(a.getType()));
    a.selectVersion(resolved.originalId.getVersion());
    return true;
  }

  /**
   * copied from {@link DefaultProjectBuilder#resolveDependencies(MavenProject, RepositorySystemSession)}
   */
  private DependencyResolutionResult resolveDependencies(MavenProject project, RepositorySystemSession session) {
    DependencyResolutionResult resolutionResult;

    try {
      ProjectDependenciesResolver dependencyResolver = getComponent(ProjectDependenciesResolver.class);
      DefaultDependencyResolutionRequest resolution = new DefaultDependencyResolutionRequest(project, session);
      resolutionResult = dependencyResolver.resolve(resolution);
    }
    catch (DependencyResolutionException e) {
      resolutionResult = e.getResult();
    }

    Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
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

  /**
   * adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)}
   */
  private void loadExtensions(MavenProject project, List<Exception> exceptions) {
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants = getLifecycleParticipants(Collections.singletonList(project));
    if (!lifecycleParticipants.isEmpty()) {
      LegacySupport legacySupport = getComponent(LegacySupport.class);
      MavenSession session = legacySupport.getSession();
      session.setCurrentProject(project);
      session.setProjects(Collections.singletonList(project));

      for (AbstractMavenLifecycleParticipant listener : lifecycleParticipants) {
        Thread.currentThread().setContextClassLoader(listener.getClass().getClassLoader());
        try {
          listener.afterProjectsRead(session);
        }
        catch (MavenExecutionException e) {
          exceptions.add(e);
        }
        finally {
          Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
      }
    }
  }

  /**
   * adapted from {@link DefaultMaven#getLifecycleParticipants(Collection)}
   */
  private Collection<AbstractMavenLifecycleParticipant> getLifecycleParticipants(Collection<MavenProject> projects) {
    Collection<AbstractMavenLifecycleParticipant> lifecycleListeners = new LinkedHashSet<AbstractMavenLifecycleParticipant>();

    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      try {
        lifecycleListeners.addAll(myContainer.lookupList(AbstractMavenLifecycleParticipant.class));
      }
      catch (ComponentLookupException e) {
        // this is just silly, lookupList should return an empty list!
        warn("Failed to lookup lifecycle participants", e);
      }

      Collection<ClassLoader> scannedRealms = new HashSet<ClassLoader>();

      for (MavenProject project : projects) {
        ClassLoader projectRealm = project.getClassRealm();

        if (projectRealm != null && scannedRealms.add(projectRealm)) {
          Thread.currentThread().setContextClassLoader(projectRealm);

          try {
            lifecycleListeners.addAll(myContainer.lookupList(AbstractMavenLifecycleParticipant.class));
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

  @Override
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

      if (activeProfiles != null) {
        result.setActiveProfiles(activeProfiles);
      }
      if (inactiveProfiles != null) {
        result.setInactiveProfiles(inactiveProfiles);
      }
      result.setCacheNotFound(true);
      result.setCacheTransferError(true);

      result.setStartTime(new Date());

      return result;
    }
    catch (MavenExecutionRequestPopulationException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public File getLocalRepositoryFile() {
    return new File(myLocalRepository.getBasedir());
  }

  @NotNull
  private MavenServerExecutionResult createExecutionResult(@Nullable File file, Maven3ExecutionResult result, DependencyNode rootNode) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    Set<MavenId> unresolvedArtifacts = new HashSet<MavenId>();

    collectProblems(file, result.getExceptions(), problems);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, unresolvedArtifacts);

    MavenModel model = new MavenModel();
    try {
      if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING) {
        //noinspection unchecked
        final List<DependencyNode> dependencyNodes = rootNode == null ? Collections.emptyList() : rootNode.getChildren();
        model = Maven3ModelConverter.convertModel(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());
      }
      else {
        final DependencyResolutionResult dependencyResolutionResult = result.getDependencyResolutionResult();
        final org.sonatype.aether.graph.DependencyNode dependencyGraph =
          dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

        final List<org.sonatype.aether.graph.DependencyNode> dependencyNodes =
          dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
        model = Maven30AetherModelConverter.convertModelWithAetherDependencyTree(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());
      }
    }
    catch (Exception e) {
      collectProblems(mavenProject.getFile(), Collections.singleton(e), problems);
    }

    RemoteNativeMaven3ProjectHolder holder = new RemoteNativeMaven3ProjectHolder(mavenProject);
    try {
      UnicastRemoteObject.exportObject(holder, 0);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    Collection<String> activatedProfiles = collectActivatedProfiles(mavenProject);

    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, Maven3ModelConverter.convertToMap(mavenProject.getModel()), holder,
                                                 activatedProfiles);
    return new MavenServerExecutionResult(data, problems, unresolvedArtifacts);
  }

  @NotNull
  private MavenGoalExecutionResult createEmbedderExecutionResult(@NotNull File file, Maven3ExecutionResult result) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();

    collectProblems(file, result.getExceptions(), problems);

    MavenGoalExecutionResult.Folders folders = new MavenGoalExecutionResult.Folders();
    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenGoalExecutionResult(false, file, folders, problems);

    folders.setSources(mavenProject.getCompileSourceRoots());
    folders.setTestSources(mavenProject.getTestCompileSourceRoots());
    folders.setResources(Maven3ModelConverter.convertResources(mavenProject.getModel().getBuild().getResources()));
    folders.setTestResources(Maven3ModelConverter.convertResources(mavenProject.getModel().getBuild().getTestResources()));

    return new MavenGoalExecutionResult(true, file, folders, problems);
  }

  private void collectProblems(@Nullable File file,
                               @NotNull Collection<Exception> exceptions,
                               @NotNull Collection<MavenProjectProblem> problems) {
    for (Throwable each : exceptions) {
      if (each == null) continue;

      MavenServerGlobals.getLogger().info(each);

      if (each instanceof IllegalStateException && each.getCause() != null) {
        each = each.getCause();
      }

      String path = file == null ? "" : file.getPath();
      if (path.isEmpty() && each instanceof ProjectBuildingException) {
        File pomFile = ((ProjectBuildingException)each).getPomFile();
        path = pomFile == null ? "" : pomFile.getPath();
      }

      if (each instanceof InvalidProjectModelException) {
        ModelValidationResult modelValidationResult = ((InvalidProjectModelException)each).getValidationResult();
        if (modelValidationResult != null) {
          for (String eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(MavenProjectProblem.createStructureProblem(path, eachValidationProblem));
          }
        }
        else {
          problems.add(MavenProjectProblem.createStructureProblem(path, each.getCause().getMessage()));
        }
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
        problems.add(MavenProjectProblem.createStructureProblem(path, causeMessage));
      }
      else {
        problems.add(MavenProjectProblem.createStructureProblem(path, each.getMessage()));
      }
    }
  }

  @NotNull
  private List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                 @NotNull List<MavenRemoteRepository> remoteRepositories, MavenToken token) {
    MavenServerUtil.checkToken(token);

    try {
      Set<Artifact> toResolve = new LinkedHashSet<Artifact>();
      for (MavenArtifactInfo each : artifacts) {
        toResolve.add(createArtifact(each));
      }

      Artifact project = getComponent(ArtifactFactory.class).createBuildArtifact("temp", "temp", "666", "pom");

      Set<Artifact> res = getComponent(ArtifactResolver.class)
        .resolveTransitively(toResolve, project, Collections.EMPTY_MAP, myLocalRepository, convertRepositories(remoteRepositories),
                             getComponent(ArtifactMetadataSource.class)).getArtifacts();

      return Maven3ModelConverter.convertArtifacts(res, new HashMap<Artifact, MavenArtifact>(), getLocalRepositoryFile());
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().info(e);
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  @Override
  public MavenArtifactResolveResult resolveArtifactsTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                                 @NotNull List<MavenRemoteRepository> remoteRepositories,
                                                                 MavenToken token) {
    return new MavenArtifactResolveResult(resolveTransitively(artifacts, remoteRepositories, token), null);
  }

  @Override
  public List<PluginResolutionResponse> resolvePlugins(@NotNull String longRunningTaskId,
                                                       @NotNull Collection<PluginResolutionRequest> pluginResolutionRequests,
                                                       MavenToken token) {
    MavenServerUtil.checkToken(token);
    List<PluginResolutionResponse> resolvedPlugins = new ArrayList<>();
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, pluginResolutionRequests.size(), myConsoleWrapper)) {
      for (PluginResolutionRequest pluginResolutionRequest : pluginResolutionRequests) {
        MavenId mavenPluginId = pluginResolutionRequest.getMavenPluginId();
        resolvedPlugins.add(resolvePlugin(task, mavenPluginId, pluginResolutionRequest.getNativeMavenProjectId()));
      }
      return resolvedPlugins;
    }
  }

  private PluginResolutionResponse resolvePlugin(LongRunningTask task, @NotNull MavenId mavenPluginId, int nativeMavenProjectId) {
    List<MavenArtifact> artifacts = new ArrayList<>();
    if (task.isCanceled()) return new PluginResolutionResponse(mavenPluginId, false, artifacts);

    try {
      Plugin mavenPlugin = new Plugin();
      mavenPlugin.setGroupId(mavenPluginId.getGroupId());
      mavenPlugin.setArtifactId(mavenPluginId.getArtifactId());
      mavenPlugin.setVersion(mavenPluginId.getVersion());
      MavenProject project = RemoteNativeMaven3ProjectHolder.findProjectById(nativeMavenProjectId);

      Plugin pluginFromProject = project.getBuild().getPluginsAsMap().get(mavenPluginId.getGroupId() + ':' + mavenPluginId.getArtifactId());
      if (pluginFromProject != null) {
        mavenPlugin.setDependencies(pluginFromProject.getDependencies());
      }

      MavenExecutionRequest request = createRequest(null, null, null);

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession repositorySystemSession = maven.newRepositorySession(request);

      PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);

      org.sonatype.aether.artifact.Artifact pluginArtifact =
        pluginDependenciesResolver.resolve(mavenPlugin, project.getRemotePluginRepositories(), repositorySystemSession);

      org.sonatype.aether.graph.DependencyNode node = pluginDependenciesResolver
        .resolve(mavenPlugin, pluginArtifact, null, project.getRemotePluginRepositories(), repositorySystemSession);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);

      for (org.sonatype.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
        if (!Objects.equals(artifact.getArtifactId(), mavenPluginId.getArtifactId()) ||
            !Objects.equals(artifact.getGroupId(), mavenPluginId.getGroupId())) {
          artifacts.add(Maven3ModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile()));
        }
      }

      task.incrementFinishedRequests();
      return new PluginResolutionResponse(mavenPluginId, true, artifacts);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().info(e);
      return new PluginResolutionResponse(mavenPluginId, false, artifacts);
    }
  }

  @NotNull
  @Override
  public List<MavenArtifact> resolveArtifacts(@NotNull String longRunningTaskId, @NotNull Collection<MavenArtifactResolutionRequest> requests, MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      return resolve(task, requests);
    }
  }

  @NotNull
  private List<MavenArtifact> resolve(@NotNull LongRunningTask task, @NotNull Collection<MavenArtifactResolutionRequest> requests) {
    List<MavenArtifact> artifacts = new ArrayList<>();
    for (MavenArtifactResolutionRequest request : requests) {
      if (task.isCanceled()) break;
      MavenArtifact artifact = doResolve(request.getArtifactInfo(), request.getRemoteRepositories());
      artifacts.add(artifact);
      task.incrementFinishedRequests();
    }
    return artifacts;
  }

  private MavenArtifact doResolve(MavenArtifactInfo info, List<MavenRemoteRepository> remoteRepositories) {
    Artifact resolved = doResolve(createArtifact(info), convertRepositories(remoteRepositories));
    return Maven3ModelConverter.convertArtifact(resolved, getLocalRepositoryFile());
  }

  private Artifact doResolve(Artifact artifact, List<ArtifactRepository> remoteRepositories) {
    try {
      return resolve(artifact, remoteRepositories);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().info(e);
    }
    return artifact;
  }

  private Artifact resolve(@NotNull final Artifact artifact, @NotNull final List<ArtifactRepository> repos)
    throws ArtifactResolutionException, ArtifactNotFoundException {

    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setRemoteRepositories(repos);
    try {
      getComponent(MavenExecutionRequestPopulator.class).populateFromSettings(request, myMavenSettings);
      getComponent(MavenExecutionRequestPopulator.class).populateDefaults(request);
    }
    catch (MavenExecutionRequestPopulationException e) {
      throw new RuntimeException(e);
    }

    getComponent(ArtifactResolver.class).resolve(artifact, request.getRemoteRepositories(), myLocalRepository);
    return artifact;
  }

  @Override
  @NotNull
  protected List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) {
    List<ArtifactRepository> result = map2ArtifactRepositories(repositories);
    if (getComponent(LegacySupport.class).getRepositorySession() == null) {
      myRepositorySystem.injectMirror(result, myMavenSettings.getMirrors());
      myRepositorySystem.injectProxy(result, myMavenSettings.getProxies());
      myRepositorySystem.injectAuthentication(result, myMavenSettings.getServers());
    }
    return result;
  }

  private Artifact createArtifact(MavenArtifactInfo info) {
    return getComponent(ArtifactFactory.class)
      .createArtifactWithClassifier(info.getGroupId(), info.getArtifactId(), info.getVersion(), info.getPackaging(), info.getClassifier());
  }

  @NotNull
  @Override
  public List<MavenGoalExecutionResult> executeGoal(@NotNull String longRunningTaskId,
                                                    @NotNull Collection<MavenGoalExecutionRequest> requests,
                                                    @NotNull String goal,
                                                    MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      return executeGoal(task, requests, goal);
    }
  }

  private List<MavenGoalExecutionResult> executeGoal(@NotNull LongRunningTask task,
                                                     @NotNull Collection<MavenGoalExecutionRequest> requests,
                                                     @NotNull String goal) {
    List<MavenGoalExecutionResult> results = new ArrayList<>();
    for (MavenGoalExecutionRequest request : requests) {
      if (task.isCanceled()) break;
      File file = request.file();
      MavenExplicitProfiles profiles = request.profiles();
      Maven3ExecutionResult result =
        doExecute(file, new ArrayList<>(profiles.getEnabledProfiles()), new ArrayList<>(profiles.getDisabledProfiles()), goal);
      results.add(createEmbedderExecutionResult(file, result));
      task.incrementFinishedRequests();
    }
    return results;
  }

  private Maven3ExecutionResult doExecute(@NotNull final File file,
                                          @NotNull final List<String> activeProfiles,
                                          @NotNull final List<String> inactiveProfiles,
                                          @NotNull final String goal) {
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles);
    request.setGoals(Collections.singletonList(goal));

    MavenExecutionResult executionResult = safeExecute(request, getComponent(Maven.class));

    return new Maven3ExecutionResult(executionResult.getProject(), filterExceptions(executionResult.getExceptions()));
  }

  private MavenExecutionResult safeExecute(MavenExecutionRequest request, Maven maven) {
    MavenLeakDetector detector = new MavenLeakDetector().mark();
    MavenExecutionResult result = maven.execute(request);
    detector.check();
    return result;
  }

  @Override
  public void release(MavenToken token) {
    MavenServerUtil.checkToken(token);
    myContainer.dispose();
  }

  @Override
  protected ArtifactRepository getLocalRepository() {
    return myLocalRepository;
  }

  public interface Computable<T> {
    T compute();
  }
}

