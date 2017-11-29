/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.maven.embedder;

import com.intellij.util.ExceptionUtil;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.manager.DefaultWagonManager;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.*;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.extension.ExtensionManager;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Plugin;
import org.apache.maven.monitor.event.DefaultEventDispatcher;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.settings.*;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.BaseLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class MavenEmbedder {
  private static final String PROP_MAVEN_HOME = "maven.home";

  private final DefaultPlexusContainer myContainer;
  private final Settings mySettings;
  private final Logger myLogger;
  private final MavenEmbedderSettings myEmbedderSettings;
  private final ArtifactRepository myLocalRepository;
  private Properties myUserProperties = new Properties();

  private MavenEmbedder(@NotNull DefaultPlexusContainer container,
                        @NotNull Settings settings,
                        @NotNull Logger logger,
                        @NotNull MavenEmbedderSettings embedderSettings) {
    myContainer = container;
    mySettings = settings;
    myLogger = logger;
    myEmbedderSettings = embedderSettings;
    myLocalRepository = createLocalRepository(embedderSettings);

    loadSettings();
  }

  private void loadSettings() {
    // copied from DefaultMaven.resolveParameters
    // copied because using original code spoils something in the container and configuration are get messed and not picked up.
    WagonManager wagonManager = getComponent(WagonManager.class);

    wagonManager.setOnline(!mySettings.isOffline());

    if (wagonManager instanceof DefaultWagonManager) {
      ((DefaultWagonManager)wagonManager).setHttpUserAgent("Apache-Maven/2.2");
    }

    Proxy proxy = mySettings.getActiveProxy();
    if (proxy != null && proxy.getHost() != null) {
      String pass = decrypt(proxy.getPassword());
      wagonManager.addProxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), proxy.getUsername(), pass, proxy.getNonProxyHosts());
    }

    for (Object each : mySettings.getServers()) {
      Server server = (Server)each;

      String passWord = decrypt(server.getPassword());
      String passPhrase = decrypt(server.getPassphrase());

      wagonManager.addAuthenticationInfo(server.getId(), server.getUsername(), passWord, server.getPrivateKey(), passPhrase);

      wagonManager.addPermissionInfo(server.getId(), server.getFilePermissions(), server.getDirectoryPermissions());

      if (server.getConfiguration() != null) {
        wagonManager.addConfiguration(server.getId(), (Xpp3Dom)server.getConfiguration());
      }
    }

    for (Object each : mySettings.getMirrors()) {
      Mirror mirror = (Mirror)each;
      if (mirror.getUrl() == null) continue;
      wagonManager.addMirror(mirror.getId(), mirror.getMirrorOf(), mirror.getUrl());
    }
    // end copied from DefaultMaven.resolveParameters
  }

  private String decrypt(String pass) {
    try {
      pass = getComponent(SecDispatcher.class, "maven").decrypt(pass);
    }
    catch (SecDispatcherException e) {
      MavenEmbedderLog.LOG.warn(e);
    }
    return pass;
  }

  private ArtifactRepository createLocalRepository(MavenEmbedderSettings generalSettings) {
    ArtifactRepositoryLayout layout = getComponent(ArtifactRepositoryLayout.class, "default");
    ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);

    String url = mySettings.getLocalRepository();
    if (!url.startsWith("file:")) url = "file://" + url;

    ArtifactRepository localRepository = new DefaultArtifactRepository("local", url, layout);

    boolean snapshotPolicySet = mySettings.isOffline();
    if (!snapshotPolicySet && generalSettings.getSnapshotUpdatePolicy() == MavenEmbedderSettings.UpdatePolicy.ALWAYS_UPDATE) {
      factory.setGlobalUpdatePolicy(ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS);
    }
    factory.setGlobalChecksumPolicy(ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

    return localRepository;
  }

  @NotNull
  public ArtifactRepository getLocalRepository() {
    return myLocalRepository;
  }

  @NotNull
  public File getLocalRepositoryFile() {
    return new File(myLocalRepository.getBasedir());
  }

  public Settings getSettings() {
    return mySettings;
  }

  @NotNull
  public MavenExecutionResult resolveProject(@NotNull final File file,
                                             @NotNull final List<String> activeProfiles,
                                             @NotNull final List<String> inactiveProfiles) {
    return resolveProject(file, activeProfiles, inactiveProfiles, Collections.<ResolutionListener>emptyList());
  }

  @NotNull
  public MavenExecutionResult resolveProject(@NotNull final File file,
                                             @NotNull final List<String> activeProfiles,
                                             @NotNull final List<String> inactiveProfiles,
                                             List<ResolutionListener> listeners) {
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles, Collections.<String>emptyList());
    ProjectBuilderConfiguration config = request.getProjectBuilderConfiguration();

    request.getGlobalProfileManager().loadSettingsProfiles(mySettings);

    ProfileManager globalProfileManager = request.getGlobalProfileManager();
    globalProfileManager.loadSettingsProfiles(request.getSettings());

    List<Exception> exceptions = new ArrayList<Exception>();
    MavenProject project = null;
    try {
      // copied from DefaultMavenProjectBuilder.buildWithDependencies
      MavenProjectBuilder builder = getComponent(MavenProjectBuilder.class);
      project = builder.build(new File(file.getPath()), config);
      builder.calculateConcreteState(project, config, false);

      // copied from DefaultLifecycleExecutor.execute
      findExtensions(project);
      // end copied from DefaultLifecycleExecutor.execute

      Artifact projectArtifact = project.getArtifact();
      Map managedVersions = project.getManagedVersionMap();
      ArtifactMetadataSource metadataSource = getComponent(ArtifactMetadataSource.class);
      project.setDependencyArtifacts(project.createArtifacts(getComponent(ArtifactFactory.class), null, null));

      ArtifactResolver resolver = getComponent(ArtifactResolver.class);
      ArtifactResolutionResult result = resolver
        .resolveTransitively(project.getDependencyArtifacts(), projectArtifact, managedVersions, myLocalRepository,
                             project.getRemoteArtifactRepositories(), metadataSource, null, listeners);
      project.setArtifacts(result.getArtifacts());
      // end copied from DefaultMavenProjectBuilder.buildWithDependencies
    }
    catch (Exception e) {
      return handleException(e);
    }

    return new MavenExecutionResult(project, exceptions);
  }

  private void findExtensions(MavenProject project) {
    // end copied from DefaultLifecycleExecutor.findExtensions
    ExtensionManager extensionManager = getComponent(ExtensionManager.class);
    for (Object each : project.getBuildExtensions()) {
      try {
        extensionManager.addExtension((Extension)each, project, myLocalRepository);
      }
      catch (PlexusContainerException e) {
        MavenEmbedderLog.LOG.error(e);
      }
      catch (ArtifactResolutionException e) {
        MavenEmbedderLog.LOG.error(e);
      }
      catch (ArtifactNotFoundException e) {
        MavenEmbedderLog.LOG.error(e);
      }
    }
    extensionManager.registerWagons();

    Map handlers = findArtifactTypeHandlers(project);
    getComponent(ArtifactHandlerManager.class).addHandlers(handlers);
  }

  @SuppressWarnings({"unchecked"})
  private Map findArtifactTypeHandlers(MavenProject project) {
    // end copied from DefaultLifecycleExecutor.findExtensions
    Map result = new HashMap();
    for (Object each : project.getBuildPlugins()) {
      Plugin eachPlugin = (Plugin)each;

      if (eachPlugin.isExtensions()) {
        try {
          PluginManager pluginManager = getComponent(PluginManager.class);
          pluginManager.verifyPlugin(eachPlugin, project, mySettings, myLocalRepository);
          result.putAll(pluginManager.getPluginComponents(eachPlugin, ArtifactHandler.ROLE));
        }
        catch (Exception e) {
          MavenEmbedderLog.LOG.info(e);
          continue;
        }

        for (Object o : result.values()) {
          ArtifactHandler handler = (ArtifactHandler)o;
          if (project.getPackaging().equals(handler.getPackaging())) {
            project.getArtifact().setArtifactHandler(handler);
          }
        }
      }
    }
    return result;
  }

  public void resolve(@NotNull final Artifact artifact, @NotNull final List<ArtifactRepository> repos)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    getComponent(ArtifactResolver.class).resolve(artifact, repos, myLocalRepository);
  }

  public Set<Artifact> resolveTransitively(@NotNull Set<Artifact> toResolve, @NotNull List<ArtifactRepository> repos)
    throws ArtifactResolutionException, ArtifactNotFoundException {
    Artifact project = getComponent(ArtifactFactory.class).createBuildArtifact("temp", "temp", "666", "pom");

    return getComponent(ArtifactResolver.class)
      .resolveTransitively(toResolve, project, Collections.EMPTY_MAP, myLocalRepository, repos, getComponent(ArtifactMetadataSource.class))
      .getArtifacts();
  }

  @NotNull
  public MavenExecutionResult execute(@NotNull final File file,
                                      @NotNull final List<String> activeProfiles,
                                      @NotNull final List<String> inactiveProfiles,
                                      @NotNull final List<String> goals,
                                      @NotNull final List<String> selectedProjects,
                                      boolean alsoMake,
                                      boolean alsoMakeDependents) {
    try {
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

      Maven maven = getComponent(Maven.class);
      Method method = maven.getClass().getDeclaredMethod("doExecute", MavenExecutionRequest.class, EventDispatcher.class);
      method.setAccessible(true);
      ReactorManager reactor = (ReactorManager)method.invoke(maven, request, request.getEventDispatcher());
      return new MavenExecutionResult(reactor.getTopLevelProject(), Collections.<Exception>emptyList());
    }
    catch (InvocationTargetException e) {
      return handleException(e.getTargetException());
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e); // should never happen
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e); // should never happen
    }
  }

  @NotNull
  public MavenExecutionResult readProjectWithModules(@NotNull final File file, List<String> activeProfiles, List<String> inactiveProfiles) {
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles, Collections.<String>emptyList());
    request.getGlobalProfileManager().loadSettingsProfiles(mySettings);
    request.setRecursive(true);

    return readProject(request);
  }

  @NotNull
  private MavenExecutionResult readProject(@NotNull final MavenExecutionRequest request) {
    ProfileManager globalProfileManager = request.getGlobalProfileManager();
    globalProfileManager.loadSettingsProfiles(request.getSettings());

    MavenProject rootProject = null;
    final List<Exception> exceptions = new ArrayList<Exception>();
    Object result = null;
    try {
      final File pomFile = new File(request.getPomFile());
      if (!pomFile.exists()) {
        throw new FileNotFoundException("File doesn't exist: " + pomFile.getPath());
      }

      final Method getProjectsMethod = DefaultMaven.class.getDeclaredMethod("getProjects", MavenExecutionRequest.class);
      getProjectsMethod.setAccessible(true);
      Maven maven = getComponent(Maven.class);
      result = getProjectsMethod.invoke(maven, request);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      return handleException(e.getTargetException());
    }
    catch (Exception e) {
      return handleException(e);
    }

    if (result != null) {
      MavenProjectBuilder builder = getComponent(MavenProjectBuilder.class);
      for (Object p : (List)result) {
        MavenProject project = (MavenProject)p;
        try {
          builder.calculateConcreteState(project, request.getProjectBuilderConfiguration(), false);
        }
        catch (ModelInterpolationException e) {
          exceptions.add(e);
        }

        if (project.isExecutionRoot()) {
          rootProject = project;
        }
      }

      if (rootProject == null && exceptions.isEmpty()) {
        throw new RuntimeException("Could't build project for unknown reason");
      }
    }

    return new MavenExecutionResult(rootProject, exceptions);
  }

  @NotNull
  public MavenExecutionResult readProject(@NotNull final File file,
                                          @NotNull final List<String> activeProfiles,
                                          @NotNull final List<String> inactiveProfiles) {
    MavenExecutionRequest request = createRequest(file, activeProfiles, inactiveProfiles, Collections.<String>emptyList());
    request.getGlobalProfileManager().loadSettingsProfiles(mySettings);
    request.setRecursive(false);

    return readProject(request);
  }

  private MavenExecutionRequest createRequest(File file, List<String> activeProfiles, List<String> inactiveProfiles, List<String> goals) {
    Properties executionProperties = myEmbedderSettings.getProperties();
    if (executionProperties == null) executionProperties = new Properties();

    DefaultEventDispatcher dispatcher = new DefaultEventDispatcher();
    dispatcher.addEventMonitor(new DefaultEventMonitor(myLogger));

    // subclassing because in DefaultMavenExecutionRequest field isRecursive is always false
    MavenExecutionRequest result = new DefaultMavenExecutionRequest(myLocalRepository, mySettings, dispatcher, goals, file.getParent(),
                                                                    createProfileManager(activeProfiles, inactiveProfiles,
                                                                                         executionProperties), executionProperties,
                                                                    myUserProperties, true) {
      private boolean myIsRecursive;

      @Override
      public boolean isRecursive() {
        return myIsRecursive;
      }

      @Override
      public void setRecursive(final boolean recursive) {
        myIsRecursive = recursive;
      }
    };

    result.setPomFile(file.getPath());
    result.setRecursive(myEmbedderSettings.isRecursive());

    return result;
  }

  private MavenExecutionResult handleException(Throwable e) {
    ExceptionUtil.rethrowUnchecked(e);

    return new MavenExecutionResult(null, Collections.singletonList((Exception)e));
  }

  private ProfileManager createProfileManager(List<String> activeProfiles, List<String> inactiveProfiles, Properties executionProperties) {
    ProfileManager profileManager = new DefaultProfileManager(getContainer(), executionProperties);
    profileManager.explicitlyActivate(activeProfiles);
    profileManager.explicitlyDeactivate(inactiveProfiles);
    return profileManager;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz) {
    try {
      return (T)getContainer().lookup(clazz.getName());
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getComponent(Class<T> clazz, String roleHint) {
    try {
      return (T)getContainer().lookup(clazz.getName(), roleHint);
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

  public PlexusContainer getContainer() {
    return myContainer;
  }

  public void release() {
    releaseResolverThreadExecutor();
    myContainer.dispose();
  }

  private void releaseResolverThreadExecutor() {
    ArtifactResolver resolver = getComponent(ArtifactResolver.class);
    @SuppressWarnings({"unchecked"}) FieldAccessor pool = new FieldAccessor(DefaultArtifactResolver.class, resolver, "resolveArtifactPool");
    try {
      final Object threadPool = pool.getField(); // an instance of a hidden copy of ThreadPoolExecutor
      threadPool.getClass().getMethod("shutdown").invoke(threadPool);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static MavenEmbedder create(@NotNull final MavenEmbedderSettings embedderSettings) {
    @NotNull final Logger logger = getLogger(embedderSettings);

    DefaultPlexusContainer container = new DefaultPlexusContainer();
    container.setClassWorld(new ClassWorld("plexus.core", embedderSettings.getClass().getClassLoader()));
    container.setLoggerManager(new BaseLoggerManager() {
      @Override
      protected Logger createLogger(final String s) {
        return logger;
      }
    });

    try {
      container.initialize();
      container.start();
    }
    catch (PlexusContainerException e) {
      MavenEmbedderLog.LOG.error(e);
      throw new RuntimeException(e);
    }

    final PlexusComponentConfigurator configurator = embedderSettings.getConfigurator();
    if (configurator != null) {
      configurator.configureComponents(container);
    }

    File mavenHome = embedderSettings.getMavenHome();
    if (mavenHome != null) {
      System.setProperty(PROP_MAVEN_HOME, mavenHome.getPath());
    }

    Settings nativeSettings = buildSettings(container, embedderSettings);

    return new MavenEmbedder(container, nativeSettings, logger, embedderSettings);
  }

  @NotNull
  private static Logger getLogger(@NotNull final MavenEmbedderSettings embedderSettings) {
    final Logger logger = embedderSettings.getLogger();
    return logger != null ? logger : new NullMavenLogger();
  }

  public static Settings buildSettings(PlexusContainer container, MavenEmbedderSettings embedderSettings) {
    File file = embedderSettings.getGlobalSettingsFile();
    if (file != null) {
      System.setProperty(MavenSettingsBuilder.ALT_GLOBAL_SETTINGS_XML_LOCATION, file.getPath());
    }

    Settings settings = null;

    try {
      MavenSettingsBuilder builder = (MavenSettingsBuilder)container.lookup(MavenSettingsBuilder.ROLE);

      File userSettingsFile = embedderSettings.getUserSettingsFile();
      if (userSettingsFile != null && userSettingsFile.exists() && !userSettingsFile.isDirectory()) {
        settings = builder.buildSettings(userSettingsFile, false);
      }

      if (settings == null) {
        settings = builder.buildSettings();
      }
    }
    catch (ComponentLookupException e) {
      MavenEmbedderLog.LOG.error(e);
    }
    catch (IOException e) {
      MavenEmbedderLog.LOG.warn(e);
    }
    catch (XmlPullParserException e) {
      MavenEmbedderLog.LOG.warn(e);
    }

    if (settings == null) {
      settings = new Settings();
    }

    if (embedderSettings.getLocalRepository() != null) {
      settings.setLocalRepository(embedderSettings.getLocalRepository().getPath());
    }
    if (settings.getLocalRepository() == null) {
      settings.setLocalRepository(System.getProperty("user.home") + "/.m2/repository");
    }

    settings.setOffline(embedderSettings.isWorkOffline());
    settings.setInteractiveMode(false);
    settings.setUsePluginRegistry(embedderSettings.isUsePluginRegistry());

    RuntimeInfo runtimeInfo = new RuntimeInfo(settings);
    runtimeInfo.setPluginUpdateOverride(embedderSettings.getPluginUpdatePolicy() == MavenEmbedderSettings.UpdatePolicy.ALWAYS_UPDATE);
    settings.setRuntimeInfo(runtimeInfo);

    return settings;
  }

  public static <T> void setImplementation(PlexusContainer container, Class<T> componentClass, Class<? extends T> implementationClass) {
    ComponentDescriptor d = container.getComponentDescriptor(componentClass.getName());
    d.setImplementation(implementationClass.getName());
  }

  public void setUserProperties(@Nullable Properties userProperties) {
    myUserProperties = userProperties == null ? new Properties() : userProperties;
  }
}

