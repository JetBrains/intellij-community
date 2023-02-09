// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtilRt;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.VersionComparatorUtil;
import org.apache.commons.cli.ParseException;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
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
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.execution.*;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.*;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.path.PathTranslator;
import org.apache.maven.model.path.UrlNormalizer;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.PluginDescriptorCache;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.profiles.activation.JdkPrefixProfileActivator;
import org.apache.maven.profiles.activation.OperatingSystemProfileActivator;
import org.apache.maven.profiles.activation.ProfileActivator;
import org.apache.maven.profiles.activation.SystemPropertyProfileActivator;
import org.apache.maven.project.*;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.*;
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
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
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
import org.jetbrains.idea.maven.server.embedder.MavenExecutionResult;
import org.jetbrains.idea.maven.server.embedder.*;
import org.jetbrains.idea.maven.server.security.MavenToken;
import org.jetbrains.idea.maven.server.utils.MavenServerParallelRunner;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Overridden maven components:
 * <p/>
 * maven-compat:
 * org.jetbrains.idea.maven.server.embedder.CustomMaven3RepositoryMetadataManager <-> org.apache.maven.artifact.repository.metadata.DefaultRepositoryMetadataManager
 * org.jetbrains.idea.maven.server.embedder.CustomMaven3ArtifactResolver <-> org.apache.maven.artifact.resolver.DefaultArtifactResolver
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
public abstract class Maven3XServerEmbedder extends Maven3ServerEmbedder {

  @NotNull private final DefaultPlexusContainer myContainer;
  @NotNull private final Settings myMavenSettings;

  private final ArtifactRepository myLocalRepository;
  private final Maven3ServerConsoleLogger myConsoleWrapper;

  private final Properties mySystemProperties;

  private volatile MavenServerProgressIndicatorWrapper myCurrentIndicator;

  private MavenWorkspaceMap myWorkspaceMap;

  private Date myBuildStartTime;

  private boolean myAlwaysUpdateSnapshots;

  @Nullable private Properties myUserProperties;

  @NotNull private final RepositorySystem myRepositorySystem;

  @NotNull private final MavenImporterSpy myImporterSpy;

  public Maven3XServerEmbedder(MavenEmbedderSettings settings) throws RemoteException {
    super(settings.getSettings());

    if (settings.getWorkingDirectory() != null) {
      System.setProperty("user.dir", settings.getWorkingDirectory());
    }

    if (settings.getMultiModuleProjectDirectory() != null) {
      System.setProperty("maven.multiModuleProjectDirectory", settings.getMultiModuleProjectDirectory());
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

    myConsoleWrapper = new Maven3ServerConsoleLogger();
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

    SettingsBuilder settingsBuilder = null;
    Class<?> cliRequestClass;
    try {
      cliRequestClass = MavenCli.class.getClassLoader().loadClass("org.apache.maven.cli.MavenCli$CliRequest");
    }
    catch (ClassNotFoundException e) {
      try {
        cliRequestClass = MavenCli.class.getClassLoader().loadClass("org.apache.maven.cli.CliRequest");
        settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
      }
      catch (ClassNotFoundException e1) {
        throw new RuntimeException("unable to find maven CliRequest class");
      }
    }

    Object cliRequest;
    try {
      List<String> commandLineOptions = new ArrayList<String>(serverSettings.getUserProperties().size());
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
        String workingDir = settings.getWorkingDirectory();
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
          throw new MavenCoreInitializationException(wrapToSerializableRuntimeException(((InvocationTargetException)e).getTargetException()), id);
        }
      }
      throw wrapToSerializableRuntimeException(e);
    }

    myContainer.getLoggerManager().setThreshold(serverSettings.getLoggingLevel());

    mySystemProperties = ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "systemProperties");

    if (serverSettings.getProjectJdk() != null) {
      mySystemProperties.setProperty("java.home", serverSettings.getProjectJdk());
    }

    if (settingsBuilder == null) {
      settingsBuilder = ReflectionUtilRt.getField(MavenCli.class, cli, SettingsBuilder.class, "settingsBuilder");
    }

    myMavenSettings = buildSettings(settingsBuilder, serverSettings, mySystemProperties,
                                    ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "userProperties"));

    myLocalRepository = createLocalRepository();

    myRepositorySystem = getComponent(RepositorySystem.class);

    MavenImporterSpy importerSpy = getComponentIfExists(MavenImporterSpy.class);

    if (importerSpy == null) {
      importerSpy = new MavenImporterSpy();
      myContainer.addComponent(importerSpy, MavenImporterSpy.class.getName());
    }
    myImporterSpy = importerSpy;
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

  @NotNull
  @Override
  protected PlexusContainer getContainer() {
    return myContainer;
  }

  private static Settings buildSettings(SettingsBuilder builder,
                                        MavenServerSettings settings,
                                        Properties systemProperties,
                                        Properties userProperties) throws RemoteException {
    SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
    if (settings.getGlobalSettingsPath() != null) {
      settingsRequest.setGlobalSettingsFile(new File(settings.getGlobalSettingsPath()));
    }
    if (settings.getUserSettingsPath() != null) {
      settingsRequest.setUserSettingsFile(new File(settings.getUserSettingsPath()));
    }
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

    if (settings.getLocalRepositoryPath() != null) {
      result.setLocalRepository(settings.getLocalRepositoryPath());
    }

    if (result.getLocalRepository() == null) {
      result.setLocalRepository(new File(System.getProperty("user.home"), ".m2/repository").getPath());
    }

    return result;
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
    org.apache.maven.project.path.PathTranslator pathTranslator = new DefaultPathTranslator();
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
          catch (Exception e) {
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

  @NotNull
  private static Model doInterpolate(@NotNull Model result, File basedir) throws RemoteException {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "3.3.1") >= 0) {
      return doInterpolate330(result, basedir);
    }
    else {
      Model model = doInterpolate325(result, basedir);
      org.apache.maven.project.path.PathTranslator pathTranslator = new DefaultPathTranslator();
      pathTranslator.alignToBaseDirectory(model, basedir);
      return model;
    }
  }

  @NotNull
  private static Model doInterpolate325(@NotNull Model result, File basedir) throws RemoteException {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomMaven3ModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenServerUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
      config.setBuildStartTime(new Date());

      Properties userProperties = new Properties();
      userProperties.putAll(getMavenAndJvmConfigProperties(basedir));
      config.setUserProperties(userProperties);

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

  @NotNull
  private static Model doInterpolate330(@NotNull Model result, File basedir) throws RemoteException {
    try {

      CustomMaven3ModelInterpolator2 interpolator = new CustomMaven3ModelInterpolator2();
      if (VersionComparatorUtil.compare(System.getProperty(MAVEN_EMBEDDER_VERSION), "3.8.5") >= 0) {
        try {
          Class<?> clazz = Class.forName("org.apache.maven.model.interpolation.DefaultModelVersionProcessor");
          Constructor<?> constructor = clazz.getConstructor();
          Object component = constructor.newInstance();
          Method processor = interpolator.getClass()
            .getMethod("setVersionPropertiesProcessor", Class.forName("org.apache.maven.model.interpolation.ModelVersionProcessor"));
          processor.invoke(interpolator, component);
        }
        catch (Exception e) {
          Maven3ServerGlobals.getLogger().error(e);
        }
      }
      //interpolator.initialize();

      Properties userProperties = new Properties();
      userProperties.putAll(getMavenAndJvmConfigProperties(basedir));
      ModelBuildingRequest request = new DefaultModelBuildingRequest();
      request.setUserProperties(userProperties);
      request.setSystemProperties(MavenServerUtil.collectSystemProperties());
      request.setBuildStartTime(new Date());
      request.setRawModel(result);
      interpolator.setPathTranslator(new PathTranslator() {
        @Override
        public String alignToBaseDirectory(String path, File basedir) {
          String result = path;
          if (path != null && basedir != null) {
            path = path.replace('\\', File.separatorChar).replace('/', File.separatorChar);
            File file = new File(path);
            if (file.isAbsolute()) {
              result = file.getPath();
            }
            else if (file.getPath().startsWith(File.separator)) {
              result = file.getAbsolutePath();
            }
            else {
              result = (new File((new File(basedir, path)).toURI().normalize())).getAbsolutePath();
            }
          }

          return result;
        }
      });

      final List<ModelProblemCollectorRequest> problems = new ArrayList<ModelProblemCollectorRequest>();
      result = interpolator.interpolateModel(result, basedir, request, new ModelProblemCollector() {
        @Override
        public void add(ModelProblemCollectorRequest request) {
          problems.add(request);
        }
      });

      for (ModelProblemCollectorRequest problem : problems) {
        if (problem.getException() != null) {
          Maven3ServerGlobals.getLogger().warn(problem.getException());
        }
      }
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().error(e);
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

  public <T> T getComponentIfExists(Class<T> clazz) {
    try {
      return (T)myContainer.lookup(clazz.getName());
    }
    catch (ComponentLookupException e) {
      return null;
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
  public @NotNull
  MavenServerPullProgressIndicator customizeAndGetProgressIndicator(@Nullable MavenWorkspaceMap workspaceMap,
                                                                    boolean failOnUnresolvedDependency,
                                                                    boolean alwaysUpdateSnapshots,
                                                                    @Nullable Properties userProperties, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);

    try {
      customizeComponents(token);

      ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
      if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
        ((CustomMaven3ArtifactFactory)artifactFactory).customize();
      }

      ((CustomMaven3ArtifactResolver)getComponent(ArtifactResolver.class)).customize(workspaceMap, failOnUnresolvedDependency);
      ((CustomMaven3RepositoryMetadataManager)getComponent(RepositoryMetadataManager.class)).customize(workspaceMap);
      //((CustomMaven3WagonManager)getComponent(WagonManager.class)).customize(failOnUnresolvedDependency);

      myWorkspaceMap = workspaceMap;

      myBuildStartTime = new Date();

      myAlwaysUpdateSnapshots = myAlwaysUpdateSnapshots || alwaysUpdateSnapshots;


      myCurrentIndicator = new MavenServerProgressIndicatorWrapper();
      myConsoleWrapper.setWrappee(myCurrentIndicator);

      try {
        UnicastRemoteObject.exportObject(myCurrentIndicator, 0);
      }
      catch (RemoteException e) {
        throw new RuntimeException(e);
      }

      myUserProperties = userProperties;
      return myCurrentIndicator;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public void customizeComponents(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {

      // replace some plexus components
      if (VersionComparatorUtil.compare("3.7.0-SNAPSHOT", getMavenVersion()) < 0) {
        myContainer.addComponent(getComponent(ArtifactFactory.class, "ide"), ArtifactFactory.ROLE);
      }
      myContainer.addComponent(getComponent(ArtifactResolver.class, "ide"), ArtifactResolver.ROLE);
      myContainer.addComponent(getComponent(RepositoryMetadataManager.class, "ide"), RepositoryMetadataManager.class.getName());
      myContainer.addComponent(getComponent(PluginDescriptorCache.class, "ide"), PluginDescriptorCache.class.getName());
      ModelInterpolator modelInterpolator = createAndPutInterpolator(myContainer);

      ModelValidator modelValidator;
      if (VersionComparatorUtil.compare(getMavenVersion(), "3.8.5") >= 0) {
        modelValidator = new CustomModelValidator385((CustomMaven3ModelInterpolator2)modelInterpolator,
                                                     (DefaultModelValidator)getComponent(ModelValidator.class));
      }
      else {
        modelValidator = getComponent(ModelValidator.class, "ide");
        myContainer.addComponent(modelValidator, ModelValidator.class.getName());
      }

      DefaultModelBuilder defaultModelBuilder = (DefaultModelBuilder)getComponent(ModelBuilder.class);
      defaultModelBuilder.setModelValidator(modelValidator);
      defaultModelBuilder.setModelInterpolator(modelInterpolator);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private ModelInterpolator createAndPutInterpolator(DefaultPlexusContainer container) {
    if (VersionComparatorUtil.compare(getMavenVersion(), "3.6.2") >= 0) {
      org.apache.maven.model.path.DefaultPathTranslator pathTranslator = new org.apache.maven.model.path.DefaultPathTranslator();
      UrlNormalizer urlNormalizer = new DefaultUrlNormalizer();
      container.addComponent(pathTranslator, PathTranslator.class.getName());
      container.addComponent(pathTranslator, PathTranslator.class, "ide");

      container.addComponent(urlNormalizer, UrlNormalizer.class.getName());
      container.addComponent(urlNormalizer, UrlNormalizer.class, "ide");

      StringSearchModelInterpolator interpolator = new CustomMaven3ModelInterpolator2();
      interpolator.setPathTranslator(pathTranslator);
      interpolator.setUrlNormalizer(urlNormalizer);

      if (VersionComparatorUtil.compare(getMavenVersion(), "3.8.5") >= 0) {
        try {
          Class<?> clazz = Class.forName("org.apache.maven.model.interpolation.ModelVersionProcessor");
          Object component = getComponent(clazz);

          container.addComponent(component, clazz.getName());
          container.addComponent(component, clazz, "ide");

          Method methodSetModelVersionProcessor = interpolator.getClass().getMethod("setVersionPropertiesProcessor", clazz);
          methodSetModelVersionProcessor.invoke(interpolator, component);
        }
        catch (Exception e) {
          try {
            Maven3ServerGlobals.getLogger().error(e);
          }
          catch (RemoteException ex) {
            throw new RuntimeException(ex);
          }
        }
      }

      return interpolator;
    }
    else {

      ModelInterpolator modelInterpolator = getComponent(ModelInterpolator.class, "ide");
      myContainer.addComponent(modelInterpolator, ModelInterpolator.class.getName());
      myContainer.addComponent(getComponent(org.apache.maven.project.interpolation.ModelInterpolator.class, "ide"),
                               org.apache.maven.project.interpolation.ModelInterpolator.ROLE);
      return modelInterpolator;
    }
  }

  @NotNull
  @Override
  public Collection<MavenServerExecutionResult> resolveProject(@NotNull Collection<File> files,
                                                               @NotNull Collection<String> activeProfiles,
                                                               @NotNull Collection<String> inactiveProfiles,
                                                               boolean forceResolveDependenciesSequentially, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      final DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);

      Collection<MavenExecutionResult> results = doResolveProject(
        files,
        new ArrayList<>(activeProfiles),
        new ArrayList<>(inactiveProfiles),
        Collections.singletonList(listener),
        forceResolveDependenciesSequentially);

      return ContainerUtilRt.map2List(results, result -> {
        try {
          return createExecutionResult(result.getPomFile(), result, listener.getRootNode());
        }
        catch (RemoteException e) {
          throw new RuntimeException(e);
        }
      });
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
                                     MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      return MavenEffectivePomDumper.evaluateEffectivePom(this, file, activeProfiles, inactiveProfiles);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  private Collection<MavenExecutionResult> doResolveProject(@NotNull final Collection<File> files,
                                                            @NotNull final List<String> activeProfiles,
                                                            @NotNull final List<String> inactiveProfiles,
                                                            final List<ResolutionListener> listeners,
                                                            boolean forceResolveDependenciesSequentially) throws RemoteException {
    final File file = !files.isEmpty() ? files.iterator().next() : null;
    final MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles, null);

    request.setUpdateSnapshots(myAlwaysUpdateSnapshots);

    final Collection<MavenExecutionResult> executionResults = new ConcurrentLinkedQueue<>();
    Map<MavenProject, MavenExecutionResult> projectsToResolveDependencies = new HashMap<>();

    executeWithMavenSession(request, (Runnable)() -> {
      try {
        MavenSession mavenSession = getComponent(LegacySupport.class).getSession();
        RepositorySystemSession repositorySession = getComponent(LegacySupport.class).getRepositorySession();
        if (repositorySession instanceof DefaultRepositorySystemSession) {
          DefaultRepositorySystemSession session = (DefaultRepositorySystemSession)repositorySession;
          myImporterSpy.setIndicator(myCurrentIndicator);
          session.setTransferListener(new TransferListenerAdapter(myCurrentIndicator));

          if (myWorkspaceMap != null) {
            session.setWorkspaceReader(new Workspace3Reader(myWorkspaceMap));
          }

          session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
          session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
        }

        List<ProjectBuildingResult> buildingResults = getProjectBuildingResults(request, files);
        fillSessionCache(mavenSession, repositorySession, buildingResults);

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


          List<ModelProblem> modelProblems = new ArrayList<ModelProblem>();

          if (buildingResult.getProblems() != null) {
            modelProblems.addAll(buildingResult.getProblems());
          }

          List<Exception> exceptions = new ArrayList<Exception>();

          loadExtensions(project, exceptions);

          project.setDependencyArtifacts(project.createArtifacts(getComponent(ArtifactFactory.class), null, null));

          if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING) {
            addMvn2CompatResults(project, exceptions, listeners, myLocalRepository, executionResults);
          }
          else {
            MavenExecutionResult executionResult = new MavenExecutionResult(project, null, exceptions, modelProblems);
            projectsToResolveDependencies.put(project, executionResult);
          }
        }

        boolean addUnresolved = System.getProperty("idea.maven.no.use.dependency.graph") == null;
        boolean runInParallel = canResolveDependenciesInParallel(forceResolveDependenciesSequentially);
        MavenServerParallelRunner.run(runInParallel, projectsToResolveDependencies.keySet(), project -> {
          MavenExecutionResult executionResult = projectsToResolveDependencies.get(project);
          final DependencyResolutionResult dependencyResolutionResult = resolveDependencies(project, repositorySession);
          Set<Artifact> artifacts = resolveArtifacts(dependencyResolutionResult, addUnresolved);
          project.setArtifacts(artifacts);
          executionResults.add(new MavenExecutionResult(project, dependencyResolutionResult, executionResult.getExceptions(), executionResult.getModelProblems()));
        });

      }
      catch (Exception e) {
        executionResults.add(handleException(e));
      }
    });

    return executionResults;
  }

  /**
   * The ThreadLocal approach was introduced in maven 3.8.2 and reverted in 3.8.4 as it caused too many side effects.
   * More details in Maven 3.8.4 release notes
   *
   * @return true if dependencies can be resolved in parallel for better performance
   */
  private static boolean canResolveDependenciesInParallel(boolean forceResolveDependenciesSequentially) {
    if (forceResolveDependenciesSequentially) {
      return false;
    }
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if ("3.8.2".equals(mavenVersion) || "3.8.3".equals(mavenVersion)) {
      return false;
    }
    return true;
  }

  private static void fillSessionCache(MavenSession mavenSession,
                                       RepositorySystemSession session,
                                       List<ProjectBuildingResult> buildingResults) {
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if (VersionComparatorUtil.compare(mavenVersion, "3.3.1") < 0) return;
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
        new Maven3WorkspaceReader(session.getWorkspaceReader(), cacheMavenModelMap));
    }
  }

  @NotNull
  private Set<Artifact> resolveArtifacts(final DependencyResolutionResult dependencyResolutionResult, boolean addUnresolvedNodes) {
    final Map<Dependency, Artifact> winnerDependencyMap = new IdentityHashMap<Dependency, Artifact>();
    Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
    Set<Dependency> addedDependencies = Collections.newSetFromMap(new IdentityHashMap<Dependency, Boolean>());
    resolveConflicts(dependencyResolutionResult, winnerDependencyMap);

    if (dependencyResolutionResult.getDependencyGraph() != null) {
      dependencyResolutionResult.getDependencyGraph().getChildren();
    }

    for (Dependency dependency : dependencyResolutionResult.getDependencies()) {
      final Artifact artifact = dependency == null ? null : winnerDependencyMap.get(dependency);
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
        final Artifact artifact = winnerDependencyMap.get(dependency);
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
                                       final Map<Dependency, Artifact> winnerDependencyMap) {
    dependencyResolutionResult.getDependencyGraph().accept(new TreeDependencyVisitor(new DependencyVisitor() {
      @Override
      public boolean visitEnter(org.eclipse.aether.graph.DependencyNode node) {
        final Object winner = node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        final Dependency dependency = node.getDependency();
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
    Collection<AbstractMavenLifecycleParticipant> lifecycleParticipants = getLifecycleParticipants(Collections.singletonList(project));
    if (!lifecycleParticipants.isEmpty()) {
      LegacySupport legacySupport = getComponent(LegacySupport.class);
      MavenSession session = legacySupport.getSession();
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
                                             @Nullable List<String> inactiveProfiles,
                                             @Nullable List<String> goals)
    throws RemoteException {

    MavenExecutionRequest result = new DefaultMavenExecutionRequest();

    try {
      getComponent(MavenExecutionRequestPopulator.class).populateFromSettings(result, myMavenSettings);

      result.setGoals(goals == null ? Collections.emptyList() : goals);

      result.setPom(file);

      getComponent(MavenExecutionRequestPopulator.class).populateDefaults(result);

      result.setSystemProperties(mySystemProperties);
      Properties userProperties = new Properties();
      if (myUserProperties != null) {
        userProperties.putAll(myUserProperties);
      }
      if (file != null) {
        userProperties.putAll(getMavenAndJvmConfigProperties(file.getParentFile()));
      }
      result.setUserProperties(userProperties);

      result.setActiveProfiles(collectActiveProfiles(result.getActiveProfiles(), activeProfiles, inactiveProfiles));
      if (inactiveProfiles != null) {
        result.setInactiveProfiles(inactiveProfiles);
      }
      result.setCacheNotFound(true);
      result.setCacheTransferError(true);

      result.setStartTime(myBuildStartTime);

      File mavenMultiModuleProjectDirectory = getMultimoduleProjectDir(file);
      result.setBaseDirectory(mavenMultiModuleProjectDirectory);

      final Method setMultiModuleProjectDirectoryMethod = getSetMultiModuleProjectDirectoryMethod(result);
      if (setMultiModuleProjectDirectoryMethod != null) {
        try {
          result.setMultiModuleProjectDirectory(mavenMultiModuleProjectDirectory);
        }
        catch (Exception e) {
          Maven3ServerGlobals.getLogger().error(e);
        }
      }

      return result;
    }
    catch (MavenExecutionRequestPopulationException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> collectActiveProfiles(@Nullable List<String> defaultActiveProfiles,
                                                    @Nullable List<String> explicitActiveProfiles,
                                                    @Nullable List<String> explicitInactiveProfiles) {
    if (defaultActiveProfiles == null || defaultActiveProfiles.isEmpty()) {
      return explicitActiveProfiles != null ? explicitActiveProfiles : Collections.emptyList();
    }

    Set<String> result = new HashSet<String>(defaultActiveProfiles);
    if (explicitInactiveProfiles != null && !explicitInactiveProfiles.isEmpty()) {
      result.removeAll(explicitInactiveProfiles);
    }

    if (explicitActiveProfiles != null) {
      result.addAll(explicitActiveProfiles);
    }

    return new ArrayList<String>(result);
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

  private static Method getSetMultiModuleProjectDirectoryMethod(MavenExecutionRequest result) {
    try {
      Method method = result.getClass().getDeclaredMethod("setMultiModuleProjectDirectory", File.class);
      method.setAccessible(true);
      return method;
    }
    catch (NoSuchMethodException | SecurityException e) {
      return null;
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
    collectProblems(file, result.getExceptions(), result.getModelProblems(), problems);

    Collection<MavenProjectProblem> unresolvedProblems = new HashSet<MavenProjectProblem>();
    collectUnresolvedArtifactProblems(file, result.getDependencyResolutionResult(), unresolvedProblems);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenServerExecutionResult(null, problems, Collections.emptySet());

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
        final org.eclipse.aether.graph.DependencyNode dependencyGraph =
          dependencyResolutionResult != null ? dependencyResolutionResult.getDependencyGraph() : null;

        final List<org.eclipse.aether.graph.DependencyNode> dependencyNodes =
          dependencyGraph != null ? dependencyGraph.getChildren() : Collections.emptyList();
        model = Maven3AetherModelConverter.convertModelWithAetherDependencyTree(
          mavenProject.getModel(), mavenProject.getCompileSourceRoots(), mavenProject.getTestCompileSourceRoots(),
          mavenProject.getArtifacts(), dependencyNodes, mavenProject.getExtensionArtifacts(), getLocalRepositoryFile());
      }
    }
    catch (Exception e) {
      collectProblems(mavenProject.getFile(), Collections.singleton(e), result.getModelProblems(), problems);
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
    return new MavenServerExecutionResult(data, problems, Collections.emptySet(), unresolvedProblems);
  }

  private void collectProblems(@Nullable File file,
                               @NotNull Collection<? extends Exception> exceptions,
                               @NotNull List<? extends ModelProblem> modelProblems,
                               @NotNull Collection<? super MavenProjectProblem> collector) throws RemoteException {
    for (Throwable each : exceptions) {
      if (each == null) continue;

      Maven3ServerGlobals.getLogger().print(ExceptionUtils.getFullStackTrace(each));
      myConsoleWrapper.info("Validation error:", each);

      Artifact problemTransferArtifact = getProblemTransferArtifact(each);
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
            collector.add(MavenProjectProblem.createStructureProblem(path, eachValidationProblem));
          }
        }
        else {
          collector.add(MavenProjectProblem.createStructureProblem(path, each.getCause().getMessage()));
        }
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
        collector.add(MavenProjectProblem.createStructureProblem(path, causeMessage));
      }
      else if (each.getStackTrace().length > 0 && each.getClass().getPackage().getName().equals("groovy.lang")) {
        myConsoleWrapper.error("Maven server structure problem", each);
        StackTraceElement traceElement = each.getStackTrace()[0];
        collector.add(MavenProjectProblem.createStructureProblem(
          traceElement.getFileName() + ":" + traceElement.getLineNumber(), each.getMessage()));
      }
      else if (problemTransferArtifact != null) {
        myConsoleWrapper.error("[server] Maven transfer artifact problem: " + problemTransferArtifact);
        String message = getRootMessage(each);
        MavenArtifact mavenArtifact = MavenModelConverter.convertArtifact(problemTransferArtifact, getLocalRepositoryFile());
        collector.add(MavenProjectProblem.createRepositoryProblem(path, message, true, mavenArtifact));
      }
      else {
        myConsoleWrapper.error("Maven server structure problem", each);
        collector.add(MavenProjectProblem.createStructureProblem(path, getRootMessage(each), true));
      }
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
      if (problem.getException() != null) {
        myConsoleWrapper.error("Maven model problem", problem.getException());
        collector.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage()));
      }
      else {
        collector.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage(), true));
      }
    }
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
        MavenArtifact mavenArtifact = MavenModelConverter.convertArtifact(artifact, getLocalRepositoryFile());
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

  @Nullable
  private static Artifact getProblemTransferArtifact(Throwable each) throws RemoteException {
    Throwable[] throwables = ExceptionUtils.getThrowables(each);
    if (throwables == null) return null;
    for (Throwable throwable : throwables) {
      if (throwable instanceof ArtifactTransferException) {
        return RepositoryUtils.toArtifact(((ArtifactTransferException)throwable).getArtifact());
      }
    }
    return null;
  }

  @NotNull
  @Override
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info,
                               @NotNull List<MavenRemoteRepository> remoteRepositories,
                               MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      return doResolve(info, remoteRepositories);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Deprecated
  @NotNull
  @Override
  public List<MavenArtifact> resolveTransitively(@NotNull final List<MavenArtifactInfo> artifacts,
                                                 @NotNull final List<MavenRemoteRepository> remoteRepositories, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);

    try {
      final MavenExecutionRequest request =
        createRequest(null, null, null, null);

      final Ref<List<MavenArtifact>> mavenArtifacts = Ref.create();
      executeWithMavenSession(request, new RunnableThrownRemote() {
        @Override
        public void run() throws RemoteException {
          mavenArtifacts.set(Maven3XServerEmbedder.this.doResolveTransitively(artifacts, remoteRepositories));
        }
      });
      return mavenArtifacts.get();
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  @Override
  public MavenArtifactResolveResult resolveArtifactTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories,
    MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      try {
        final MavenExecutionRequest request = createRequest(null, null, null, null);

        final Ref<List<MavenArtifact>> mavenArtifacts = Ref.create();
        executeWithMavenSession(request, (Runnable)() -> {
          try {
            mavenArtifacts.set(this.doResolveTransitivelyWithError(artifacts, remoteRepositories));
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        return new MavenArtifactResolveResult(mavenArtifacts.get(), null);
      }
      catch (Exception e) {
        Maven3ServerGlobals.getLogger().error(e);
        Artifact transferArtifact = getProblemTransferArtifact(e);
        String message = getRootMessage(e);
        MavenProjectProblem problem;
        if (transferArtifact != null) {
          MavenArtifact mavenArtifact = MavenModelConverter.convertArtifact(transferArtifact, getLocalRepositoryFile());
          problem = MavenProjectProblem.createRepositoryProblem("", message, true, mavenArtifact);
        }
        else {
          problem = MavenProjectProblem.createStructureProblem("", message);
        }
        return new MavenArtifactResolveResult(Collections.emptyList(), problem);
      }
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  private List<MavenArtifact> doResolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                    @NotNull List<MavenRemoteRepository> remoteRepositories) throws RemoteException {

    try {
      Set<Artifact> toResolve = new LinkedHashSet<Artifact>();
      for (MavenArtifactInfo each : artifacts) {
        toResolve.add(createArtifact(each));
      }

      Artifact project = getComponent(ArtifactFactory.class).createBuildArtifact("temp", "temp", "666", "pom");

      Set<Artifact> res = getComponent(ArtifactResolver.class)
        .resolveTransitively(toResolve, project, Collections.emptyMap(), myLocalRepository, convertRepositories(remoteRepositories),
                             getComponent(ArtifactMetadataSource.class)).getArtifacts();

      return MavenModelConverter.convertArtifacts(res, new HashMap<Artifact, MavenArtifact>(), getLocalRepositoryFile());
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().info(e);
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  private List<MavenArtifact> doResolveTransitivelyWithError(@NotNull List<MavenArtifactInfo> artifacts,
                                                             @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, ArtifactResolutionException, ArtifactNotFoundException {
    Set<Artifact> toResolve = new LinkedHashSet<Artifact>();
    for (MavenArtifactInfo each : artifacts) {
      toResolve.add(createArtifact(each));
    }

    Artifact project = getComponent(ArtifactFactory.class).createBuildArtifact("temp", "temp", "666", "pom");

    Set<Artifact> res = getComponent(ArtifactResolver.class)
      .resolveTransitively(toResolve, project, Collections.emptyMap(), myLocalRepository, convertRepositories(remoteRepositories),
                           getComponent(ArtifactMetadataSource.class)).getArtifacts();

    return MavenModelConverter.convertArtifacts(res, new HashMap<Artifact, MavenArtifact>(), getLocalRepositoryFile());
  }

  @Override
  public Collection<MavenArtifact> resolvePlugin(@NotNull final MavenPlugin plugin,
                                                 @NotNull final List<MavenRemoteRepository> repositories,
                                                 int nativeMavenProjectId,
                                                 final boolean transitive, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
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
      request.setTransferListener(new TransferListenerAdapter(myCurrentIndicator));

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession repositorySystemSession = maven.newRepositorySession(request);
      myImporterSpy.setIndicator(myCurrentIndicator);
      PluginDependenciesResolver pluginDependenciesResolver = getComponent(PluginDependenciesResolver.class);

      org.eclipse.aether.artifact.Artifact pluginArtifact =
        pluginDependenciesResolver.resolve(mavenPlugin, project.getRemotePluginRepositories(), repositorySystemSession);

      org.eclipse.aether.graph.DependencyNode node = pluginDependenciesResolver
        .resolve(mavenPlugin, pluginArtifact, null, project.getRemotePluginRepositories(), repositorySystemSession);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);

      List<MavenArtifact> res = new ArrayList<MavenArtifact>();

      for (org.eclipse.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
        if (!Objects.equals(artifact.getArtifactId(), plugin.getArtifactId()) ||
            !Objects.equals(artifact.getGroupId(), plugin.getGroupId())) {
          res.add(MavenModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile()));
        }
      }

      return res;
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().warn(e);
      return Collections.emptyList();
    }
  }

  @Override
  @Nullable
  public MavenModel readModel(File file, MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      Map<String, Object> inputOptions = new HashMap<String, Object>();
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
          Maven3ServerGlobals.getLogger().warn(e);
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
          return MavenModelConverter.convertModel(model, null);
        }
        catch (Exception e) {
          Maven3ServerGlobals.getLogger().warn(e);
        }
      }
    }
    catch (Exception e) {
      Maven3ServerGlobals.getLogger().warn(e);
    }
    return null;
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
    throws
    ArtifactResolutionException,
    ArtifactNotFoundException,
    RemoteException,
    org.eclipse.aether.resolution.ArtifactResolutionException {

    final String mavenVersion = getMavenVersion();
    myImporterSpy.setIndicator(myCurrentIndicator);
    // org.eclipse.aether.RepositorySystem.newResolutionRepositories() method doesn't exist in aether-api-0.9.0.M2.jar used before maven 3.2.5
    // see https://youtrack.jetbrains.com/issue/IDEA-140208 for details
    if (USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING || VersionComparatorUtil.compare(mavenVersion, "3.2.5") < 0) {
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
    else {
      final MavenExecutionRequest request =
        createRequest(null, null, null, null);
      for (ArtifactRepository artifactRepository : repos) {
        request.addRemoteRepository(artifactRepository);
      }

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession repositorySystemSession = maven.newRepositorySession(request);

      initLogging(myConsoleWrapper);

      // do not use request.getRemoteRepositories() here,
      // it can be broken after DefaultMaven#newRepositorySession => MavenRepositorySystem.injectMirror invocation
      final RemoteRepositoryManager remoteRepositoryManager = getComponent(RemoteRepositoryManager.class);
      final org.eclipse.aether.RepositorySystem repositorySystem = getComponent(org.eclipse.aether.RepositorySystem.class);
      List<RemoteRepository> repositories = RepositoryUtils.toRepos(repos);
      repositories =
        remoteRepositoryManager.aggregateRepositories(repositorySystemSession, new ArrayList<RemoteRepository>(), repositories, false);

      final ArtifactResult artifactResult = repositorySystem.resolveArtifact(
        repositorySystemSession, new ArtifactRequest(RepositoryUtils.toArtifact(artifact), repositories, null));

      return RepositoryUtils.toArtifact(artifactResult.getArtifact());
    }
  }

  protected abstract void initLogging(Maven3ServerConsoleLogger consoleWrapper);

  @Override
  @NotNull
  protected List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) throws RemoteException {
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
  public MavenServerExecutionResult execute(@NotNull File file,
                                            @NotNull Collection<String> activeProfiles,
                                            @NotNull Collection<String> inactiveProfiles,
                                            @NotNull List<String> goals,
                                            @NotNull List<String> selectedProjects,
                                            boolean alsoMake,
                                            boolean alsoMakeDependents, MavenToken token)
    throws RemoteException {
    MavenServerUtil.checkToken(token);
    try {
      MavenExecutionResult result =
        doExecute(file, new ArrayList<String>(activeProfiles), new ArrayList<String>(inactiveProfiles), goals, selectedProjects, alsoMake,
                  alsoMakeDependents);

      return createExecutionResult(file, result, null);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
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
  public void reset(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      if (myCurrentIndicator != null) {
        UnicastRemoteObject.unexportObject(myCurrentIndicator, false);
      }
      myCurrentIndicator = null;
      myConsoleWrapper.setWrappee(null);

      final ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
      if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
        ((CustomMaven3ArtifactFactory)artifactFactory).reset();
      }
      final ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
      if (artifactResolver instanceof CustomMaven3ArtifactResolver) {
        ((CustomMaven3ArtifactResolver)artifactResolver).reset();
      }
      final RepositoryMetadataManager repositoryMetadataManager = getComponent(RepositoryMetadataManager.class);
      if (repositoryMetadataManager instanceof CustomMaven3RepositoryMetadataManager) {
        ((CustomMaven3RepositoryMetadataManager)repositoryMetadataManager).reset();
      }
      //((CustomWagonManager)getComponent(WagonManager.class)).reset();
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
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

  @Override
  public void clearCaches(MavenToken token) {
    MavenServerUtil.checkToken(token);
    // do nothing
  }

  @Override
  public void clearCachesFor(final MavenId projectId, MavenToken token) {
    MavenServerUtil.checkToken(token);
    // do nothing
  }

  @Override
  protected ArtifactRepository getLocalRepository() {
    return myLocalRepository;
  }
}

