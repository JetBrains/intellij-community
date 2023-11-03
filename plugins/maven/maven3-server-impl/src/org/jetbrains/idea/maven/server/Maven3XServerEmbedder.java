// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtilRt;
import com.intellij.util.text.VersionComparatorUtil;
import org.apache.commons.cli.ParseException;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.execution.*;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.*;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.path.PathTranslator;
import org.apache.maven.model.path.UrlNormalizer;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.PluginDescriptorCache;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.repository.RepositorySystem;
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
import org.jetbrains.idea.maven.server.embedder.*;
import org.jetbrains.idea.maven.server.security.MavenToken;
import org.jetbrains.idea.maven.server.utils.Maven3SettingsBuilder;
import org.jetbrains.idea.maven.server.utils.Maven3XProjectResolver;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.*;

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

  private final boolean myAlwaysUpdateSnapshots;

  @NotNull private final RepositorySystem myRepositorySystem;

  @NotNull private final Maven3ImporterSpy myImporterSpy;

  @NotNull protected final MavenEmbedderSettings myEmbedderSettings;

  public Maven3XServerEmbedder(MavenEmbedderSettings settings) {
    super(settings.getSettings());

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

    if (settingsBuilder == null) {
      settingsBuilder = ReflectionUtilRt.getField(MavenCli.class, cli, SettingsBuilder.class, "settingsBuilder");
    }

    myMavenSettings = Maven3SettingsBuilder.buildSettings(
      settingsBuilder,
      serverSettings,
      mySystemProperties,
      ReflectionUtilRt.getField(cliRequestClass, cliRequest, Properties.class, "userProperties")
    );

    myLocalRepository = createLocalRepository();

    myRepositorySystem = getComponent(RepositorySystem.class);

    Maven3ImporterSpy importerSpy = getComponentIfExists(Maven3ImporterSpy.class);

    if (importerSpy == null) {
      importerSpy = new Maven3ImporterSpy();
      myContainer.addComponent(importerSpy, Maven3ImporterSpy.class.getName());
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

    if (serverSettings.getGlobalSettingsPath() != null && new File(serverSettings.getGlobalSettingsPath()).isFile()) {
      commandLineOptions.add("-gs");
      commandLineOptions.add(serverSettings.getGlobalSettingsPath());
    }

    if (serverSettings.getUserSettingsPath() != null && new File(serverSettings.getUserSettingsPath()).isFile()) {
      commandLineOptions.add("-s");
      commandLineOptions.add(serverSettings.getUserSettingsPath());
    }

    if (serverSettings.isOffline()) {
      commandLineOptions.add("-o");
    }

    return commandLineOptions;
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

  @NotNull
  @Override
  protected PlexusContainer getContainer() {
    return myContainer;
  }

  private static Maven3ExecutionResult handleException(Exception e) {
    return new Maven3ExecutionResult(Collections.singletonList(e));
  }

  private static Maven3ExecutionResult handleException(MavenProject mavenProject, Exception e) {
    return new Maven3ExecutionResult(mavenProject, Collections.singletonList(e));
  }

  private static List<Exception> filterExceptions(List<Throwable> list) {
    for (Throwable throwable : list) {
      if (!(throwable instanceof Exception)) {
        throw new RuntimeException(throwable);
      }
    }

    return (List<Exception>)((List)list);
  }

  protected <T> void addComponent(T componemnt, Class<T> clazz) {
    myContainer.addComponent(componemnt, clazz.getName());
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

  public <T> T getComponentIfExists(Class<T> clazz, String roleHint) {
    try {
      return (T)myContainer.lookup(clazz.getName(), roleHint);
    }
    catch (ComponentLookupException e) {
      return null;
    }
  }

  public <T> List<T> getComponents(Class<T> clazz) {
    try {
      return (List<T>)myContainer.lookupList(clazz.getName());
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
    }
  }

  protected void customizeComponents(@Nullable MavenWorkspaceMap workspaceMap) {
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

      ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
      if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
        ((CustomMaven3ArtifactFactory)artifactFactory).customize();
      }

      ((CustomMaven3ArtifactResolver)getComponent(ArtifactResolver.class)).customize(workspaceMap);
      ((CustomMaven3RepositoryMetadataManager)getComponent(RepositoryMetadataManager.class)).customize(workspaceMap);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  protected void resetComponents() {
    ArtifactFactory artifactFactory = getComponent(ArtifactFactory.class);
    if (artifactFactory instanceof CustomMaven3ArtifactFactory) {
      ((CustomMaven3ArtifactFactory)artifactFactory).reset();
    }
    ArtifactResolver artifactResolver = getComponent(ArtifactResolver.class);
    if (artifactResolver instanceof CustomMaven3ArtifactResolver) {
      ((CustomMaven3ArtifactResolver)artifactResolver).reset();
    }
    RepositoryMetadataManager repositoryMetadataManager = getComponent(RepositoryMetadataManager.class);
    if (repositoryMetadataManager instanceof CustomMaven3RepositoryMetadataManager) {
      ((CustomMaven3RepositoryMetadataManager)repositoryMetadataManager).reset();
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
          MavenServerGlobals.getLogger().error(e);
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

  @Nullable
  @Override
  public String evaluateEffectivePom(@NotNull File file,
                                     @NotNull ArrayList<String> activeProfiles,
                                     @NotNull ArrayList<String> inactiveProfiles,
                                     MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      return Maven3EffectivePomDumper.evaluateEffectivePom(this, file, activeProfiles, inactiveProfiles);
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @NotNull
  @Override
  public ArrayList<MavenServerExecutionResult> resolveProjects(@NotNull String longRunningTaskId,
                                                               @NotNull ProjectResolutionRequest request, MavenToken token) {
    MavenServerUtil.checkToken(token);
    List<File> files = request.getPomFiles();
    List<String> activeProfiles = request.getActiveProfiles();
    List<String> inactiveProfiles = request.getInactiveProfiles();
    MavenWorkspaceMap workspaceMap = request.getWorkspaceMap();
    boolean updateSnapshots = myAlwaysUpdateSnapshots || request.updateSnapshots();
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, files.size(), myConsoleWrapper)) {
      Maven3XProjectResolver projectResolver = new Maven3XProjectResolver(
        this,
        updateSnapshots,
        myImporterSpy,
        task.getIndicator(),
        workspaceMap,
        myConsoleWrapper,
        myLocalRepository,
        request.getUserProperties(),
        canResolveDependenciesInParallel()
      );
      try {
        customizeComponents(workspaceMap);
        return projectResolver.resolveProjects(task, files, activeProfiles, inactiveProfiles);
      }
      finally {
        resetComponents();
      }
    }
  }

  /**
   * The ThreadLocal approach was introduced in maven 3.8.2 and reverted in 3.8.4 as it caused too many side effects.
   * More details in Maven 3.8.4 release notes
   *
   * @return true if dependencies can be resolved in parallel for better performance
   */
  private boolean canResolveDependenciesInParallel() {
    if (myEmbedderSettings.forceResolveDependenciesSequentially()) {
      return false;
    }
    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    if ("3.8.2".equals(mavenVersion) || "3.8.3".equals(mavenVersion)) {
      return false;
    }

    return true;
  }

  @Override
  public MavenExecutionRequest createRequest(@Nullable File file,
                                             @Nullable List<String> activeProfiles,
                                             @Nullable List<String> inactiveProfiles,
                                             @NotNull Properties customProperties) {

    MavenExecutionRequest result = new DefaultMavenExecutionRequest();

    try {
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

      Method setMultiModuleProjectDirectoryMethod = getSetMultiModuleProjectDirectoryMethod(result);
      if (setMultiModuleProjectDirectoryMethod != null) {
        try {
          result.setMultiModuleProjectDirectory(mavenMultiModuleProjectDirectory);
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().error(e);
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

    Set<String> result = new HashSet<>(defaultActiveProfiles);
    if (explicitInactiveProfiles != null && !explicitInactiveProfiles.isEmpty()) {
      result.removeAll(explicitInactiveProfiles);
    }

    if (explicitActiveProfiles != null) {
      result.addAll(explicitActiveProfiles);
    }

    return new ArrayList<>(result);
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
  private MavenGoalExecutionResult createEmbedderExecutionResult(@NotNull File file, Maven3ExecutionResult result) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();

    collectProblems(file, result.getExceptions(), result.getModelProblems(), problems);

    MavenGoalExecutionResult.Folders folders = new MavenGoalExecutionResult.Folders();
    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenGoalExecutionResult(false, file, folders, problems);

    folders.setSources(mavenProject.getCompileSourceRoots());
    folders.setTestSources(mavenProject.getTestCompileSourceRoots());
    folders.setResources(Maven3ModelConverter.convertResources(mavenProject.getModel().getBuild().getResources()));
    folders.setTestResources(Maven3ModelConverter.convertResources(mavenProject.getModel().getBuild().getTestResources()));

    return new MavenGoalExecutionResult(true, file, folders, problems);
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
        myConsoleWrapper.error("Maven model problem", problemException);
        collector.add(MavenProjectProblem.createStructureProblem(source, problem.getMessage()));
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

    if (ex instanceof InvalidProjectModelException) {
      ModelValidationResult modelValidationResult = ((InvalidProjectModelException)ex).getValidationResult();
      if (modelValidationResult != null) {
        for (String eachValidationProblem : modelValidationResult.getMessages()) {
          result.add(MavenProjectProblem.createStructureProblem(path, eachValidationProblem));
        }
      }
      else {
        result.add(MavenProjectProblem.createStructureProblem(path, ex.getCause().getMessage()));
      }
    }
    else if (ex instanceof ProjectBuildingException) {
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
      MavenArtifact mavenArtifact = Maven3ModelConverter.convertArtifact(problemTransferArtifact, getLocalRepositoryFile());
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

  @NotNull
  @Override
  public MavenArtifactResolveResult resolveArtifactsTransitively(
    @NotNull final ArrayList<MavenArtifactInfo> artifacts,
    @NotNull final ArrayList<MavenRemoteRepository> remoteRepositories,
    MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      try {
        MavenExecutionRequest request = createRequest(null, null, null);

        final Ref<List<MavenArtifact>> mavenArtifacts = Ref.create();
        executeWithMavenSession(request, () -> {
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
        MavenServerGlobals.getLogger().error(e);
        Artifact transferArtifact = getProblemTransferArtifact(e);
        String message = getRootMessage(e);
        MavenProjectProblem problem;
        if (transferArtifact != null) {
          MavenArtifact mavenArtifact = Maven3ModelConverter.convertArtifact(transferArtifact, getLocalRepositoryFile());
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
  private List<MavenArtifact> doResolveTransitivelyWithError(@NotNull List<MavenArtifactInfo> artifacts,
                                                             @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    Set<Artifact> toResolve = new LinkedHashSet<>();
    for (MavenArtifactInfo each : artifacts) {
      toResolve.add(createArtifact(each));
    }

    Artifact project = getComponent(ArtifactFactory.class).createBuildArtifact("temp", "temp", "666", "pom");

    Set<Artifact> res = getComponent(ArtifactResolver.class)
      .resolveTransitively(toResolve, project, Collections.emptyMap(), myLocalRepository, convertRepositories(remoteRepositories),
                           getComponent(ArtifactMetadataSource.class)).getArtifacts();

    return Maven3ModelConverter.convertArtifacts(res, new HashMap<>(), getLocalRepositoryFile());
  }

  @Override
  public ArrayList<PluginResolutionResponse> resolvePlugins(@NotNull String longRunningTaskId,
                                                            @NotNull ArrayList<PluginResolutionRequest> pluginResolutionRequests,
                                                            boolean forceUpdateSnapshots,
                                                            MavenToken token) {
    MavenServerUtil.checkToken(token);

    String mavenVersion = System.getProperty(MAVEN_EMBEDDER_VERSION);
    boolean runInParallel = canResolveDependenciesInParallel() && VersionComparatorUtil.compare(mavenVersion, "3.6.0") >= 0;
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, pluginResolutionRequests.size(), myConsoleWrapper)) {
      MavenExecutionRequest request = createRequest(null, null, null);
      request.setTransferListener(new Maven3TransferListenerAdapter(task.getIndicator()));
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

        MavenProject project = RemoteNativeMaven3ProjectHolder.findProjectById(nativeMavenProjectId);
        List<RemoteRepository> remoteRepos = project.getRemotePluginRepositories();

        Plugin pluginFromProject = project.getBuild().getPluginsAsMap().get(groupId + ':' + artifactId);
        List<Dependency> pluginDependencies =
          null == pluginFromProject ? Collections.emptyList() : pluginFromProject.getDependencies();

        PluginResolutionData resolution = new PluginResolutionData(mavenPluginId, pluginDependencies, remoteRepos);
        resolutions.add(resolution);
      }
      List<PluginResolutionResponse> results = ParallelRunner.execute(runInParallel, resolutions, resolution ->
        resolvePlugin(task, resolution.mavenPluginId, resolution.pluginDependencies, resolution.remoteRepos, session)
      );
      return new ArrayList<>(results);
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
    MavenServerStatsCollector.pluginResolve(mavenPluginId.toString());
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

      DependencyNode node = pluginDependenciesResolver.resolve(plugin, pluginArtifact, null, remoteRepos, session);

      PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
      node.accept(nlg);


      for (org.eclipse.aether.artifact.Artifact artifact : nlg.getArtifacts(true)) {
        if (!Objects.equals(artifact.getArtifactId(), plugin.getArtifactId()) ||
            !Objects.equals(artifact.getGroupId(), plugin.getGroupId())) {
          artifacts.add(Maven3ModelConverter.convertArtifact(RepositoryUtils.toArtifact(artifact), getLocalRepositoryFile()));
        }
      }

      task.incrementFinishedRequests();
      return new PluginResolutionResponse(mavenPluginId, true, artifacts);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().warn(e);
      return new PluginResolutionResponse(mavenPluginId, false, artifacts);
    }
    finally {
      long totalTime = System.currentTimeMillis() - startTime;
      MavenServerGlobals.getLogger().debug("Resolved plugin " + mavenPluginId + " in " + totalTime + " ms");
    }
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
          return Maven3ModelConverter.convertModel(model, null);
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

  @NotNull
  @Override
  public ArrayList<MavenArtifact> resolveArtifacts(@NotNull String longRunningTaskId,
                                                   @NotNull ArrayList<MavenArtifactResolutionRequest> requests,
                                                   MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      return doResolveArtifacts(task, requests);
    }
  }

  @NotNull
  private ArrayList<MavenArtifact> doResolveArtifacts(@NotNull LongRunningTask task,
                                                      @NotNull Collection<MavenArtifactResolutionRequest> requests) {
    try {
      ArrayList<MavenArtifact> artifacts = new ArrayList<>();
      for (MavenArtifactResolutionRequest request : requests) {
        if (task.isCanceled()) break;
        MavenArtifact artifact = doResolveArtifact(request.getArtifactInfo(), request.getRemoteRepositories(), task.getIndicator());
        artifacts.add(artifact);
        task.incrementFinishedRequests();
      }
      return artifacts;
    }
    catch (Exception e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  private MavenArtifact doResolveArtifact(MavenArtifactInfo info,
                                          List<MavenRemoteRepository> remoteRepositories,
                                          MavenServerConsoleIndicatorImpl indicator) {
    Artifact resolved = doResolveArtifact(createArtifact(info), convertRepositories(remoteRepositories), indicator);
    return Maven3ModelConverter.convertArtifact(resolved, getLocalRepositoryFile());
  }

  private Artifact doResolveArtifact(Artifact artifact,
                                     List<ArtifactRepository> remoteRepositories,
                                     MavenServerConsoleIndicatorImpl indicator) {
    try {
      return tryResolveArtifact(artifact, remoteRepositories, indicator);
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().info(e);
    }
    return artifact;
  }

  private Artifact tryResolveArtifact(@NotNull Artifact artifact,
                                      @NotNull List<ArtifactRepository> repos,
                                      MavenServerConsoleIndicatorImpl indicator)
    throws
    ArtifactResolutionException,
    ArtifactNotFoundException,
    RemoteException,
    org.eclipse.aether.resolution.ArtifactResolutionException {

    String mavenVersion = getMavenVersion();
    myImporterSpy.setIndicator(indicator);
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
      MavenExecutionRequest request = createRequest(null, null, null);
      for (ArtifactRepository artifactRepository : repos) {
        request.addRemoteRepository(artifactRepository);
      }

      DefaultMaven maven = (DefaultMaven)getComponent(Maven.class);
      RepositorySystemSession repositorySystemSession = maven.newRepositorySession(request);

      initLogging(myConsoleWrapper);

      // do not use request.getRemoteRepositories() here,
      // it can be broken after DefaultMaven#newRepositorySession => MavenRepositorySystem.injectMirror invocation
      RemoteRepositoryManager remoteRepositoryManager = getComponent(RemoteRepositoryManager.class);
      org.eclipse.aether.RepositorySystem repositorySystem = getComponent(org.eclipse.aether.RepositorySystem.class);
      List<RemoteRepository> repositories = RepositoryUtils.toRepos(repos);
      repositories =
        remoteRepositoryManager.aggregateRepositories(repositorySystemSession, new ArrayList<RemoteRepository>(), repositories, false);

      ArtifactResult artifactResult = repositorySystem.resolveArtifact(
        repositorySystemSession, new ArtifactRequest(RepositoryUtils.toArtifact(artifact), repositories, null));

      return RepositoryUtils.toArtifact(artifactResult.getArtifact());
    }
  }

  protected abstract void initLogging(Maven3ServerConsoleLogger consoleWrapper);

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
  public ArrayList<MavenGoalExecutionResult> executeGoal(@NotNull String longRunningTaskId,
                                                         @NotNull ArrayList<MavenGoalExecutionRequest> requests,
                                                         @NotNull String goal,
                                                         MavenToken token) {
    MavenServerUtil.checkToken(token);
    try (LongRunningTask task = newLongRunningTask(longRunningTaskId, requests.size(), myConsoleWrapper)) {
      return executeGoal(task, requests, goal);
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

    MavenExecutionResult executionResult = safeExecute(mavenExecutionRequest, getComponent(Maven.class));

    Maven3ExecutionResult result =
      new Maven3ExecutionResult(executionResult.getProject(), filterExceptions(executionResult.getExceptions()));
    return createEmbedderExecutionResult(file, result);
  }

  private static MavenExecutionResult safeExecute(MavenExecutionRequest request, Maven maven) {
    MavenLeakDetector detector = new MavenLeakDetector().mark();
    MavenExecutionResult result = maven.execute(request);
    detector.check();
    return result;
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
  protected ArtifactRepository getLocalRepository() {
    return myLocalRepository;
  }
}

