/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
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
import org.apache.maven.profiles.activation.*;
import org.apache.maven.project.*;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
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
import org.jetbrains.idea.maven.server.embedder.MavenExecutionResult;
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

  private volatile MavenServerProgressIndicator myCurrentIndicator;

  private MavenWorkspaceMap myWorkspaceMap;

  private Date myBuildStartTime;

  private boolean myAlwaysUpdateSnapshots;

  public Maven30ServerEmbedderImpl(MavenServerSettings settings) throws RemoteException {
    super(settings);

    File mavenHome = settings.getMavenHome();
    if (mavenHome != null) {
      System.setProperty("maven.home", mavenHome.getPath());
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

    Class cliRequestClass;
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

      if (settings.getLoggingLevel() == MavenServerConsole.LEVEL_DEBUG) {
        commandLineOptions.add("-X");
        commandLineOptions.add("-e");
      }
      else if (settings.getLoggingLevel() == MavenServerConsole.LEVEL_DISABLED) {
        commandLineOptions.add("-q");
      }

      String mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS);
      if (mavenEmbedderCliOptions != null) {
        commandLineOptions.addAll(StringUtil.splitHonorQuotes(mavenEmbedderCliOptions, ' '));
      }
      if (commandLineOptions.contains("-U") || commandLineOptions.contains("--update-snapshots")) {
        myAlwaysUpdateSnapshots = true;
      }

      //noinspection unchecked
      Constructor constructor = cliRequestClass.getDeclaredConstructor(String[].class, ClassWorld.class);
      constructor.setAccessible(true);
      //noinspection SSBasedInspection
      cliRequest = constructor.newInstance(commandLineOptions.toArray(new String[commandLineOptions.size()]), classWorld);

      for (String each : new String[]{"initialize", "cli", "logging", "properties", "container"}) {
        Method m = MavenCli.class.getDeclaredMethod(each, cliRequestClass);
        m.setAccessible(true);
        m.invoke(cli, cliRequest);
      }
    }
    catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }

    // reset threshold
    myContainer = ReflectionUtil.getField(MavenCli.class, cli, DefaultPlexusContainer.class, "container");
    myContainer.getLoggerManager().setThreshold(settings.getLoggingLevel());

    mySystemProperties = ReflectionUtil.getField(cliRequestClass, cliRequest, Properties.class, "systemProperties");

    if (settings.getProjectJdk() != null) {
      mySystemProperties.setProperty("java.home", settings.getProjectJdk());
    }

    myMavenSettings =
      buildSettings(ReflectionUtil.getField(MavenCli.class, cli, SettingsBuilder.class, "settingsBuilder"), settings, mySystemProperties,
                    ReflectionUtil.getField(cliRequestClass, cliRequest, Properties.class, "userProperties"));

    myLocalRepository = createLocalRepository();
  }

  private static Settings buildSettings(SettingsBuilder builder,
                                        MavenServerSettings settings,
                                        Properties systemProperties,
                                        Properties userProperties) throws RemoteException {
    SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
    settingsRequest.setGlobalSettingsFile(settings.getGlobalSettingsFile());
    settingsRequest.setUserSettingsFile(settings.getUserSettingsFile());
    settingsRequest.setSystemProperties(systemProperties);
    settingsRequest.setUserProperties(userProperties);

    Settings result = new Settings();
    try {
      result = builder.build(settingsRequest).getEffectiveSettings();
    }
    catch (SettingsBuildingException e) {
      Maven3ServerGlobals.getLogger().info(e);
    }

    result.setOffline(settings.isOffline());

    if (settings.getLocalRepository() != null) {
      result.setLocalRepository(settings.getLocalRepository().getPath());
    }

    if (result.getLocalRepository() == null) {
      result.setLocalRepository(new File(SystemProperties.getUserHome(), ".m2/repository").getPath());
    }

    return result;
  }

  private static void warn(String message, Throwable e) {
    try {
      Maven3ServerGlobals.getLogger().warn(new RuntimeException(message, e));
    }
    catch (RemoteException e1) {
      throw new RuntimeException(e1);
    }
  }

  private static MavenExecutionResult handleException(Throwable e) {
    if (e instanceof Error) throw (Error)e;

    return new MavenExecutionResult(Collections.singletonList((Exception)e));
  }

  private static Collection<String> collectActivatedProfiles(MavenProject mavenProject)
    throws RemoteException {
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
      Maven3ServerGlobals.getLogger().info(e);
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
  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) throws RemoteException {
    Model result = MavenModelConverter.toNativeModel(model);
    result = doInterpolate(result, basedir);

    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(result, basedir);

    return MavenModelConverter.convertModel(result, null);
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) throws RemoteException {
    Model result = MavenModelConverter.toNativeModel(model);
    new DefaultModelInheritanceAssembler().assembleModelInheritance(result, MavenModelConverter.toNativeModel(parentModel));
    return MavenModelConverter.convertModel(result, null);
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       MavenExplicitProfiles explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) throws RemoteException {
    Model nativeModel = MavenModelConverter.toNativeModel(model);

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
          catch (ProfileActivationException e) {
            Maven3ServerGlobals.getLogger().warn(e);
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

    return new ProfileApplicationResult(MavenModelConverter.convertModel(nativeModel, null),
                                        new MavenExplicitProfiles(collectProfilesIds(activatedProfiles),
                                                                  collectProfilesIds(deactivatedProfiles))
    );
  }

  private static Model doInterpolate(Model result, File basedir) throws RemoteException {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomMaven3ModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenServerUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
      config.setBuildStartTime(new Date());

      result = interpolator.interpolate(result, basedir, config, false);
    }
    catch (ModelInterpolationException e) {
      Maven3ServerGlobals.getLogger().warn(e);
    }
    catch (InitializationException e) {
      Maven3ServerGlobals.getLogger().error(e);
    }
    return result;
  }

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Collection<String> result = new THashSet<String>();
    for (Profile each : profiles) {
      if (each.getId() != null) {
        result.add(each.getId());
      }
    }
    return result;
  }

  private static ProfileActivator[] getProfileActivators(File basedir) throws RemoteException {
    SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenServerUtil.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      Maven3ServerGlobals.getLogger().error(e);
      return new ProfileActivator[0];
    }

    return new ProfileActivator[]{new MyFileProfileActivator(basedir), sysPropertyActivator, new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz, String roleHint) {
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
    //ArtifactRepositoryLayout layout = getComponent(ArtifactRepositoryLayout.class, "default");
    //ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);
    //
    //String url = myMavenSettings.getLocalRepository();
    //if (!url.startsWith("file:")) url = "file://" + url;
    //
    //ArtifactRepository localRepository = factory.createArtifactRepository("local", url, layout, null, null);
    //
    //boolean snapshotPolicySet = myMavenSettings.isOffline();
    //if (!snapshotPolicySet && snapshotUpdatePolicy == MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE) {
    //  factory.setGlobalUpdatePolicy(ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS);
    //}
    //factory.setGlobalChecksumPolicy(ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);
    //
    //return localRepository;
  }

  @Override
  public void customize(@Nullable MavenWorkspaceMap workspaceMap,
                        boolean failOnUnresolvedDependency,
                        @NotNull MavenServerConsole console,
                        @NotNull MavenServerProgressIndicator indicator,
                        boolean alwaysUpdateSnapshots) throws RemoteException {

    try {
      customizeComponents();

      ((CustomMaven3ArtifactFactory)getComponent(ArtifactFactory.class)).customize();
      ((CustomMaven30ArtifactResolver)getComponent(ArtifactResolver.class)).customize(workspaceMap, failOnUnresolvedDependency);
      ((CustomMaven3RepositoryMetadataManager)getComponent(RepositoryMetadataManager.class)).customize(workspaceMap);
      //((CustomMaven3WagonManager)getComponent(WagonManager.class)).customize(failOnUnresolvedDependency);

      myWorkspaceMap = workspaceMap;

      myBuildStartTime = new Date();

      myAlwaysUpdateSnapshots = myAlwaysUpdateSnapshots || alwaysUpdateSnapshots;

      setConsoleAndIndicator(console, new MavenServerProgressIndicatorWrapper(indicator));
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  public void customizeComponents() throws RemoteException {
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
  }

  private void setConsoleAndIndicator(MavenServerConsole console, MavenServerProgressIndicator indicator) {
    myConsoleWrapper.setWrappee(console);
    myCurrentIndicator = indicator;
  }

  @NotNull
  @Override
  public Collection<MavenServerExecutionResult> resolveProject(@NotNull final Collection<File> files,
                                                               @NotNull Collection<String> activeProfiles,
                                                               @NotNull Collection<String> inactiveProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    final DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);

    Collection<MavenExecutionResult> results =
      doResolveProject(files, new ArrayList<String>(activeProfiles), new ArrayList<String>(inactiveProfiles),
                       Collections.<ResolutionListener>singletonList(listener));
    return ContainerUtil.mapNotNull(results, new Function<MavenExecutionResult, MavenServerExecutionResult>() {
      @Override
      public MavenServerExecutionResult fun(MavenExecutionResult result) {
        try {
          return createExecutionResult(result.getPomFile(), result, listener.getRootNode());
        }
        catch (RemoteException e) {
          ExceptionUtil.rethrowAllAsUnchecked(e);
        }
        return null;
      }
    });
  }

  @Nullable
  @Override
  public String evaluateEffectivePom(@NotNull File file, @NotNull List<String> activeProfiles, @NotNull List<String> inactiveProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    return MavenEffectivePomDumper.evaluateEffectivePom(this, file, activeProfiles, inactiveProfiles);
  }

  public void executeWithMavenSession(MavenExecutionRequest request, Runnable runnable) {
    DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
    RepositorySystemSession repositorySession = maven.newRepositorySession(request);

    request.getProjectBuildingRequest().setRepositorySession(repositorySession);

    MavenSession mavenSession = new MavenSession(myContainer, repositorySession, request, new DefaultMavenExecutionResult());
    LegacySupport legacySupport = getComponent(LegacySupport.class);

    MavenSession oldSession = legacySupport.getSession();

    legacySupport.setSession(mavenSession);

    /** adapted from {@link DefaultMaven#doExecute(MavenExecutionRequest)} */
    try {
      for (AbstractMavenLifecycleParticipant listener : getLifecycleParticipants(Collections.<MavenProject>emptyList())) {
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
  public Collection<MavenExecutionResult> doResolveProject(@NotNull final Collection<File> files,
                                                           @NotNull final List<String> activeProfiles,
                                                           @NotNull final List<String> inactiveProfiles,
                                                           final List<ResolutionListener> listeners) throws RemoteException {
    final File file = files.size() == 1 ? files.iterator().next() : null;
    final MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles, null);

    request.setUpdateSnapshots(myAlwaysUpdateSnapshots);

    final Collection<MavenExecutionResult> executionResults = ContainerUtil.newArrayList();

    executeWithMavenSession(request, new Runnable() {
      @Override
      public void run() {
        try {
          RepositorySystemSession repositorySession = getComponent(LegacySupport.class).getRepositorySession();
          if (repositorySession instanceof DefaultRepositorySystemSession) {
            ((DefaultRepositorySystemSession)repositorySession)
              .setTransferListener(new Maven30TransferListenerAdapter(myCurrentIndicator));

            if (myWorkspaceMap != null) {
              ((DefaultRepositorySystemSession)repositorySession).setWorkspaceReader(new Maven30WorkspaceReader(myWorkspaceMap));
            }
          }

          List<ProjectBuildingResult> buildingResults = getProjectBuildingResults(request, files);

          for (ProjectBuildingResult buildingResult : buildingResults) {
            MavenProject project = buildingResult.getProject();

            if (project == null) {
              List<Exception> exceptions = new ArrayList<Exception>();
              for (ModelProblem problem : buildingResult.getProblems()) {
                exceptions.add(problem.getException());
              }
              MavenExecutionResult mavenExecutionResult = new MavenExecutionResult(buildingResult.getPomFile(), exceptions);
              executionResults.add(mavenExecutionResult);
              continue;
            }

            List<Exception> exceptions = new ArrayList<Exception>();
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
              final DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
              final List<Dependency> dependencies = dependencyResolutionResult.getDependencies();

              Set<Artifact> artifacts = new LinkedHashSet<Artifact>(dependencies.size());
              for (Dependency dependency : dependencies) {
                final Artifact artifact = RepositoryUtils.toArtifact(dependency.getArtifact());
                artifact.setScope(dependency.getScope());
                artifact.setOptional(dependency.isOptional());
                artifacts.add(artifact);
                resolveAsModule(artifact);
              }

              project.setArtifacts(artifacts);
              executionResults.add(new MavenExecutionResult(project, dependencyResolutionResult, exceptions));
            }
          }
        }
        catch (Exception e) {
          executionResults.add(handleException(e));
        }
      }
    });

    return executionResults;
  }

  private boolean resolveAsModule(Artifact a) {
    MavenWorkspaceMap map = myWorkspaceMap;
    if (map == null) return false;

    MavenWorkspaceMap.Data resolved = map.findFileAndOriginalId(MavenModelConverter.createMavenId(a));
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
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants = getLifecycleParticipants(Arrays.asList(project));
    if (!lifecycleParticipants.isEmpty()) {
      LegacySupport legacySupport = getComponent(LegacySupport.class);
      MavenSession session = legacySupport.getSession();
      session.setCurrentProject(project);
      session.setProjects(Arrays.asList(project));

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

  public MavenExecutionRequest createRequest(@Nullable File file,
                                             @Nullable List<String> activeProfiles,
                                             @Nullable List<String> inactiveProfiles,
                                             @Nullable List<String> goals)
    throws RemoteException {
    //Properties executionProperties = myMavenSettings.getProperties();
    //if (executionProperties == null) {
    //  executionProperties = new Properties();
    //}

    MavenExecutionRequest result = new DefaultMavenExecutionRequest();

    try {
      getComponent(MavenExecutionRequestPopulator.class).populateFromSettings(result, myMavenSettings);

      result.setGoals(goals == null ? Collections.<String>emptyList() : goals);

      result.setPom(file);

      getComponent(MavenExecutionRequestPopulator.class).populateDefaults(result);

      result.setSystemProperties(mySystemProperties);

      if (activeProfiles != null) {
        result.setActiveProfiles(activeProfiles);
      }
      if (inactiveProfiles != null) {
        result.setInactiveProfiles(inactiveProfiles);
      }
      result.setCacheNotFound(true);
      result.setCacheTransferError(true);

      result.setStartTime(myBuildStartTime);

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
  private MavenServerExecutionResult createExecutionResult(@Nullable File file, MavenExecutionResult result, DependencyNode rootNode)
    throws RemoteException {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    THashSet<MavenId> unresolvedArtifacts = new THashSet<MavenId>();

    validate(file, result.getExceptions(), problems, unresolvedArtifacts);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, unresolvedArtifacts);

    MavenModel model = new MavenModel();
    try {
      if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING) {
        //noinspection unchecked
        final List<DependencyNode> dependencyNodes = rootNode == null ? Collections.emptyList() : rootNode.getChildren();
        model = MavenModelConverter.convertModel(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());
      }
      else {
        final DependencyResolutionResult dependencyResolutionResult = result.getDependencyResolutionResult();
        final org.sonatype.aether.graph.DependencyNode dependencyGraph =
          dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

        final List<org.sonatype.aether.graph.DependencyNode> dependencyNodes =
          dependencyGraph != null ? dependencyGraph.getChildren() : Collections.<org.sonatype.aether.graph.DependencyNode>emptyList();
        model = Maven30AetherModelConverter.convertModelWithAetherDependencyTree(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());
      }
    }
    catch (Exception e) {
      validate(mavenProject.getFile(), Collections.singleton(e), problems, null);
    }

    RemoteNativeMavenProjectHolder holder = new RemoteNativeMavenProjectHolder(mavenProject);
    try {
      UnicastRemoteObject.exportObject(holder, 0);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    Collection<String> activatedProfiles = collectActivatedProfiles(mavenProject);

    MavenServerExecutionResult.ProjectData data =
      new MavenServerExecutionResult.ProjectData(model, MavenModelConverter.convertToMap(mavenProject.getModel()), holder,
                                                 activatedProfiles);
    return new MavenServerExecutionResult(data, problems, unresolvedArtifacts);
  }

  private void validate(@Nullable File file,
                        @NotNull Collection<Exception> exceptions,
                        @NotNull Collection<MavenProjectProblem> problems,
                        @Nullable Collection<MavenId> unresolvedArtifacts) throws RemoteException {
    for (Throwable each : exceptions) {
      if(each == null) continue;

      Maven3ServerGlobals.getLogger().info(each);

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
          for (Object eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(MavenProjectProblem.createStructureProblem(path, (String)eachValidationProblem));
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
    if (unresolvedArtifacts != null) {
      unresolvedArtifacts.addAll(retrieveUnresolvedArtifactIds());
    }
  }

  private Set<MavenId> retrieveUnresolvedArtifactIds() {
    Set<MavenId> result = new THashSet<MavenId>();
    // TODO collect unresolved artifacts
    //((CustomMaven3WagonManager)getComponent(WagonManager.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    //((CustomMaven30ArtifactResolver)getComponent(ArtifactResolver.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    return result;
  }

  @NotNull
  @Override
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info, @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    return doResolve(info, remoteRepositories);
  }

  @NotNull
  @Override
  public List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                 @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {

    try {
      Set<Artifact> toResolve = new LinkedHashSet<Artifact>();
      for (MavenArtifactInfo each : artifacts) {
        toResolve.add(createArtifact(each));
      }

      Artifact project = getComponent(ArtifactFactory.class).createBuildArtifact("temp", "temp", "666", "pom");

      Set<Artifact> res = getComponent(ArtifactResolver.class)
        .resolveTransitively(toResolve, project, Collections.EMPTY_MAP, myLocalRepository, convertRepositories(remoteRepositories),
                             getComponent(ArtifactMetadataSource.class)).getArtifacts();

      return MavenModelConverter.convertArtifacts(res, new THashMap<Artifact, MavenArtifact>(), getLocalRepositoryFile());
    }
    catch (ArtifactResolutionException e) {
      Maven3ServerGlobals.getLogger().info(e);
    }
    catch (ArtifactNotFoundException e) {
      Maven3ServerGlobals.getLogger().info(e);
    }
    catch (Exception e) {
      throw rethrowException(e);
    }

    return Collections.emptyList();
  }

  @Override
  public Collection<MavenArtifact> resolvePlugin(@NotNull final MavenPlugin plugin,
                                                 @NotNull final List<MavenRemoteRepository> repositories,
                                                 int nativeMavenProjectId,
                                                 final boolean transitive) throws RemoteException, MavenServerProcessCanceledException {
    try {
      Plugin mavenPlugin = new Plugin();
      mavenPlugin.setGroupId(plugin.getGroupId());
      mavenPlugin.setArtifactId(plugin.getArtifactId());
      mavenPlugin.setVersion(plugin.getVersion());
      MavenProject project = RemoteNativeMavenProjectHolder.findProjectById(nativeMavenProjectId);

      Plugin pluginFromProject = project.getBuild().getPluginsAsMap().get(plugin.getGroupId() + ':' + plugin.getArtifactId());
      if (pluginFromProject != null) {
        mavenPlugin.setDependencies(pluginFromProject.getDependencies());
      }

      final MavenExecutionRequest request =
        createRequest(null, null, null, null);

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession repositorySystemSession = maven.newRepositorySession(request);

      PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);

      org.sonatype.aether.artifact.Artifact pluginArtifact =
        pluginDependenciesResolver.resolve(mavenPlugin, project.getRemotePluginRepositories(), repositorySystemSession);

      org.sonatype.aether.graph.DependencyNode node = pluginDependenciesResolver
        .resolve(mavenPlugin, pluginArtifact, null, project.getRemotePluginRepositories(), repositorySystemSession);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);

      List<MavenArtifact> res = new ArrayList<MavenArtifact>();

      for (org.sonatype.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
        if (!Comparing.equal(artifact.getArtifactId(), plugin.getArtifactId()) ||
            !Comparing.equal(artifact.getGroupId(), plugin.getGroupId())) {
          res.add(MavenModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile()));
        }
      }

      return res;
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().info(e);
      return Collections.emptyList();
    }
  }

  private MavenArtifact doResolve(MavenArtifactInfo info, List<MavenRemoteRepository> remoteRepositories) throws RemoteException {
    Artifact resolved = doResolve(createArtifact(info), convertRepositories(remoteRepositories));
    return MavenModelConverter.convertArtifact(resolved, getLocalRepositoryFile());
  }

  private Artifact doResolve(Artifact artifact, List<ArtifactRepository> remoteRepositories) throws RemoteException {
    try {
      return resolve(artifact, remoteRepositories);
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().info(e);
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

  @NotNull
  protected List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) throws RemoteException {
    List<ArtifactRepository> result = new ArrayList<ArtifactRepository>();
    for (MavenRemoteRepository each : repositories) {
      try {
        ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);
        result.add(ProjectUtils.buildArtifactRepository(MavenModelConverter.toNativeRepository(each), factory, myContainer));
      }
      catch (InvalidRepositoryException e) {
        Maven3ServerGlobals.getLogger().warn(e);
      }
    }
    return result;
  }

  private Artifact createArtifact(MavenArtifactInfo info) {
    return getComponent(ArtifactFactory.class)
      .createArtifactWithClassifier(info.getGroupId(), info.getArtifactId(), info.getVersion(), info.getPackaging(), info.getClassifier());
  }

  @NotNull
  @Override
  public MavenServerExecutionResult execute(@NotNull File file,
                                            @NotNull Collection<String> activeProfiles,
                                            @NotNull Collection<String> inactiveProfiles,
                                            @NotNull List<String> goals,
                                            @NotNull List<String> selectedProjects,
                                            boolean alsoMake,
                                            boolean alsoMakeDependents) throws RemoteException, MavenServerProcessCanceledException {
    MavenExecutionResult result =
      doExecute(file, new ArrayList<String>(activeProfiles), new ArrayList<String>(inactiveProfiles), goals, selectedProjects, alsoMake,
                alsoMakeDependents);

    return createExecutionResult(file, result, null);
  }

  private MavenExecutionResult doExecute(@NotNull final File file,
                                         @NotNull final List<String> activeProfiles,
                                         @NotNull final List<String> inactiveProfiles,
                                         @NotNull final List<String> goals,
                                         @NotNull final List<String> selectedProjects,
                                         boolean alsoMake,
                                         boolean alsoMakeDependents) throws RemoteException {
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles, goals);

    if (!selectedProjects.isEmpty()) {
      request.setRecursive(true);
      request.setSelectedProjects(selectedProjects);
      if (alsoMake && alsoMakeDependents) {
        request.setMakeBehavior(ReactorManager.MAKE_BOTH_MODE);
      }
      else if (alsoMake) {
        request.setMakeBehavior(ReactorManager.MAKE_MODE);
      }
      else if (alsoMakeDependents) {
        request.setMakeBehavior(ReactorManager.MAKE_DEPENDENTS_MODE);
      }
    }

    org.apache.maven.execution.MavenExecutionResult executionResult = safeExecute(request, getComponent(Maven.class));

    return new MavenExecutionResult(executionResult.getProject(), filterExceptions(executionResult.getExceptions()));
  }

  private org.apache.maven.execution.MavenExecutionResult safeExecute(MavenExecutionRequest request, Maven maven) throws RemoteException {
    MavenLeakDetector detector = new MavenLeakDetector().mark();
    org.apache.maven.execution.MavenExecutionResult result = maven.execute(request);
    detector.check();
    return result;
  }

  @Override
  public void reset() throws RemoteException {
    try {
      setConsoleAndIndicator(null, null);

      final ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
      if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
        ((CustomMaven3ArtifactFactory)artifactFactory).reset();
      }
      final ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
      if(artifactResolver instanceof CustomMaven30ArtifactResolver) {
        ((CustomMaven30ArtifactResolver)artifactResolver).reset();
      }
      final RepositoryMetadataManager repositoryMetadataManager = getComponent(RepositoryMetadataManager.class);
      if(repositoryMetadataManager instanceof CustomMaven3RepositoryMetadataManager) {
        ((CustomMaven3RepositoryMetadataManager)repositoryMetadataManager).reset();
      }
      //((CustomWagonManager)getComponent(WagonManager.class)).reset();
    }
    catch (Exception e) {
      throw rethrowException(e);
    }
  }

  @Override
  public void release() throws RemoteException {
    myContainer.dispose();
  }

  public void clearCaches() throws RemoteException {
    // do nothing
  }

  public void clearCachesFor(final MavenId projectId) throws RemoteException {
    // do nothing
  }

  @Override
  protected ArtifactRepository getLocalRepository() {
    return myLocalRepository;
  }

  public interface Computable<T> {
    T compute();
  }

  private class MyLogger implements org.sonatype.aether.spi.log.Logger {
    @Override
    public boolean isDebugEnabled() {
      return myConsoleWrapper.isDebugEnabled();
    }

    @Override
    public void debug(String s) {
      myConsoleWrapper.debug(s);
    }

    @Override
    public void debug(String s, Throwable throwable) {
      myConsoleWrapper.debug(s, throwable);
    }

    @Override
    public boolean isWarnEnabled() {
      return myConsoleWrapper.isWarnEnabled();
    }

    @Override
    public void warn(String s) {
      myConsoleWrapper.warn(s);
    }

    @Override
    public void warn(String s, Throwable throwable) {
      myConsoleWrapper.debug(s, throwable);
    }
  }
}

