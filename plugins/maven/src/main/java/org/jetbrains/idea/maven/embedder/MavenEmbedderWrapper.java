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
package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import hidden.edu.emory.mathcs.backport.java.util.concurrent.ThreadPoolExecutor;
import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
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
import org.apache.maven.model.Build;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.monitor.event.DefaultEventDispatcher;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactFactory;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MavenEmbedderWrapper {
  private final DefaultPlexusContainer myContainer;
  private final Settings mySettings;
  private final MavenLogger myLogger;
  private final ArtifactRepository myLocalRepository;

  private volatile MavenProgressIndicator myCurrentIndicator;

  public MavenEmbedderWrapper(@NotNull DefaultPlexusContainer container,
                              @NotNull Settings settings,
                              @NotNull MavenLogger logger,
                              @NotNull MavenGeneralSettings generalSettings) {
    myContainer = container;
    mySettings = settings;
    myLogger = logger;
    myLocalRepository = createLocalRepository(generalSettings);
    configureContainer();

    loadSettings();
  }

  private void loadSettings() {
    // copied from DefaultMaven.resolveParamaters
    // copied because using original code spoils something in the container and configuration are get messed and not picked up.
    DefaultWagonManager wagonManager = (DefaultWagonManager)getComponent(WagonManager.class);
    wagonManager.setHttpUserAgent("Apache-Maven/2.2");

    Proxy proxy = mySettings.getActiveProxy();
    if (proxy != null && proxy.getHost() != null) {
      String pass = decrypt(proxy.getPassword());
      wagonManager.addProxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), proxy.getUsername(), pass, proxy.getNonProxyHosts());
    }

    for (Object each : mySettings.getServers()) {
      Server server = (Server)each;

      String passWord = decrypt(server.getPassword());
      String passPhrase = decrypt(server.getPassphrase());

      wagonManager.addAuthenticationInfo(server.getId(), server.getUsername(), passWord,
                                         server.getPrivateKey(), passPhrase);

      wagonManager.addPermissionInfo(server.getId(), server.getFilePermissions(),
                                     server.getDirectoryPermissions());

      if (server.getConfiguration() != null) {
        wagonManager.addConfiguration(server.getId(), (Xpp3Dom)server.getConfiguration());
      }
    }

    for (Object each : mySettings.getMirrors()) {
      Mirror mirror = (Mirror)each;
      wagonManager.addMirror(mirror.getId(), mirror.getMirrorOf(), mirror.getUrl());
    }
    // end copied from DefaultMaven.resolveParamaters
  }

  private String decrypt(String pass) {
    try {
      pass = getComponent(SecDispatcher.class, "maven").decrypt(pass);
    }
    catch (SecDispatcherException e) {
      MavenLog.LOG.warn(e);
    }
    return pass;
  }

  private ArtifactRepository createLocalRepository(MavenGeneralSettings generalSettings) {
    ArtifactRepositoryLayout layout = getComponent(ArtifactRepositoryLayout.class, "default");
    ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);

    String url = mySettings.getLocalRepository();
    if (!url.startsWith("file:")) url = "file://" + url;

    ArtifactRepository localRepository = new DefaultArtifactRepository("local", url, layout);

    boolean snapshotPolicySet = mySettings.isOffline();
    if (!snapshotPolicySet && generalSettings.getSnapshotUpdatePolicy() == MavenExecutionOptions.SnapshotUpdatePolicy.ALWAYS_UPDATE) {
      factory.setGlobalUpdatePolicy(ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS);
    }
    factory.setGlobalChecksumPolicy(ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

    return localRepository;
  }

  public MavenExecutionResult resolveProject(@NotNull final VirtualFile file,
                                             @NotNull final Collection<String> activeProfiles) throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenExecutionResult>() {
      public MavenExecutionResult execute() throws Exception {
        //loadSettings();

        MavenExecutionRequest request = createRequest(file, activeProfiles, Collections.EMPTY_LIST);
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
          ArtifactResolutionResult result = resolver.resolveTransitively(project.getDependencyArtifacts(),
                                                                         projectArtifact, managedVersions,
                                                                         myLocalRepository,
                                                                         project.getRemoteArtifactRepositories(),
                                                                         metadataSource);
          project.setArtifacts(result.getArtifacts());
          // end copied from DefaultMavenProjectBuilder.buildWithDependencies
        }
        catch (ProjectBuildingException e) {
          exceptions.add(e);
        }
        catch (ArtifactResolutionException e) {
          exceptions.add(e);
        }
        catch (ArtifactNotFoundException e) {
          exceptions.add(e);
        }

        return new MavenExecutionResult(project, retrieveUnresolvedArtifactIds(), exceptions);
      }
    });
  }

  private void findExtensions(MavenProject project) {
    // end copied from DefaultLifecycleExecutor.findExtensions
    ExtensionManager extensionManager = getComponent(ExtensionManager.class);
    for (Object each : project.getBuildExtensions()) {
      try {
        extensionManager.addExtension((Extension)each, project, myLocalRepository);
      }
      catch (PlexusContainerException e) {
      }
      catch (ArtifactResolutionException e) {
      }
      catch (ArtifactNotFoundException e) {
      }
    }
    extensionManager.registerWagons();

    Map handlers = findArtifactTypeHandlers(project);
    getComponent(ArtifactHandlerManager.class).addHandlers(handlers);
  }

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
          MavenLog.LOG.info(e);
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

  public void resolve(@NotNull final Artifact artifact, @NotNull final List<MavenRemoteRepository> remoteRepositories)
    throws MavenProcessCanceledException {
    doExecute(new Executor<Object>() {
      public Object execute() throws Exception {
        try {
          getComponent(ArtifactResolver.class).resolve(artifact, convertRepositories(remoteRepositories), myLocalRepository);
        }
        catch (Exception e) {
          MavenLog.LOG.info(e);
        }
        return null;
      }
    });
  }

  public Artifact resolve(@NotNull final MavenId id,
                          @NotNull final String type,
                          @Nullable final String classifier,
                          @NotNull final List<MavenRemoteRepository> remoteRepositories)
    throws MavenProcessCanceledException {
    return doExecute(new Executor<Artifact>() {
      public Artifact execute() throws Exception {
        return doResolve(id, type, classifier, convertRepositories(remoteRepositories));
      }
    });
  }

  private Artifact doResolve(MavenId id, String type, String classifier, List<ArtifactRepository> remoteRepositories) {
    Artifact artifact = getComponent(ArtifactFactory.class).createArtifactWithClassifier(id.getGroupId(),
                                                                                         id.getArtifactId(),
                                                                                         id.getVersion(),
                                                                                         type,
                                                                                         classifier);
    try {
      getComponent(ArtifactResolver.class).resolve(artifact, remoteRepositories, myLocalRepository);
      return artifact;
    }
    catch (Exception e) {
      MavenLog.LOG.info(e);
    }
    return artifact;
  }

  private List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) {
    List<ArtifactRepository> result = new ArrayList<ArtifactRepository>();
    for (MavenRemoteRepository each : repositories) {
      try {
        ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);
        result.add(ProjectUtils.buildArtifactRepository(each.toRepository(), factory, getContainer()));
      }
      catch (InvalidRepositoryException e) {
        MavenLog.LOG.warn(e);
      }
    }
    return result;
  }

  public boolean resolvePlugin(@NotNull final MavenPlugin plugin, @NotNull final MavenProject nativeMavenProject, final boolean transitive)
    throws MavenProcessCanceledException {
    return doExecute(new Executor<Boolean>() {
      public Boolean execute() throws Exception {
        try {
          Plugin mavenPlugin = new Plugin();
          mavenPlugin.setGroupId(plugin.getGroupId());
          mavenPlugin.setArtifactId(plugin.getArtifactId());
          mavenPlugin.setVersion(plugin.getVersion());
          PluginDescriptor result =
            getComponent(PluginManager.class).verifyPlugin(mavenPlugin, nativeMavenProject, mySettings, myLocalRepository);
          if (!transitive) return true;

          for (ComponentDependency each : (List<ComponentDependency>)result.getDependencies()) {
            List repos = nativeMavenProject.getRemoteArtifactRepositories();
            // todo try to use parallel downloading
            doResolve(new MavenId(each.getGroupId(), each.getArtifactId(), each.getVersion()), each.getType(), null, repos);
          }
        }
        catch (Exception e) {
          MavenLog.LOG.info(e);
          return false;
        }
        return true;
      }
    });
  }

  @NotNull
  public MavenExecutionResult execute(@NotNull final VirtualFile file,
                                      @NotNull final Collection<String> activeProfiles,
                                      @NotNull final List<String> goals)
    throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenExecutionResult>() {
      public MavenExecutionResult execute() throws Exception {
        MavenExecutionRequest request = createRequest(file, activeProfiles, goals);
        Maven maven = getComponent(Maven.class);
        Method method = maven.getClass().getDeclaredMethod("doExecute", MavenExecutionRequest.class, EventDispatcher.class);
        method.setAccessible(true);
        ReactorManager reactor = (ReactorManager)method.invoke(maven, request, request.getEventDispatcher());
        return new MavenExecutionResult(reactor.getTopLevelProject(), retrieveUnresolvedArtifactIds(), Collections.EMPTY_LIST);
      }
    });
  }

  private MavenExecutionRequest createRequest(VirtualFile virtualFile, Collection<String> profiles, List<String> goals) {
    Properties executionProperties = getExecutionProperties();

    DefaultEventDispatcher dispatcher = new DefaultEventDispatcher();
    dispatcher.addEventMonitor(new DefaultEventMonitor(myLogger));

    MavenExecutionRequest result = new DefaultMavenExecutionRequest(myLocalRepository,
                                                                    mySettings,
                                                                    dispatcher, goals,
                                                                    virtualFile.getParent().getPath(),
                                                                    createProfileManager(profiles, executionProperties),
                                                                    executionProperties,
                                                                    new Properties(), true);

    result.setPomFile(virtualFile.getPath());
    result.setRecursive(false);

    return result;
  }

  private Properties getExecutionProperties() {
    return MavenEmbedderFactory.collectSystemProperties();
  }

  private ProfileManager createProfileManager(Collection<String> activeProfiles, Properties executionProperties) {
    ProfileManager profileManager = new DefaultProfileManager(getContainer(), executionProperties);
    profileManager.explicitlyActivate(new ArrayList<String>(activeProfiles));
    return profileManager;
  }

  private Set<MavenId> retrieveUnresolvedArtifactIds() {
    Set<MavenId> result = new THashSet<MavenId>();
    ((CustomWagonManager)getComponent(WagonManager.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    return result;
  }

  public File getLocalRepositoryFile() {
    return new File(myLocalRepository.getBasedir());
  }

  public <T> T getComponent(Class<T> clazz) {
    try {
      return (T)getContainer().lookup(clazz.getName());
    }
    catch (ComponentLookupException e) {
      throw new RuntimeException(e);
    }
  }

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
    FieldAccessor pool = new FieldAccessor(DefaultArtifactResolver.class, resolver, "resolveArtifactPool");
    ((ThreadPoolExecutor)pool.getField()).shutdown();
  }

  private interface Executor<T> {
    T execute() throws Exception;
  }

  private <T> T doExecute(final Executor<T> executor) throws MavenProcessCanceledException {
    final Ref<T> result = new Ref<T>();
    final boolean[] cancelled = new boolean[1];
    final Throwable[] exception = new Throwable[1];

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          result.set(executor.execute());
        }
        catch (ProcessCanceledException e) {
          cancelled[0] = true;
        }
        catch (Throwable e) {
          exception[0] = e;
        }
      }
    });

    MavenProgressIndicator indicator = myCurrentIndicator;
    while (true) {
      indicator.checkCanceled();
      try {
        future.get(50, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e.getCause());
      }
      catch (InterruptedException e) {
        throw new MavenProcessCanceledException();
      }

      if (future.isDone()) break;
    }

    if (cancelled[0]) throw new MavenProcessCanceledException();
    if (exception[0] != null) {
      throw getRethrowable(exception[0]);
    }

    return result.get();
  }

  private RuntimeException getRethrowable(Throwable throwable) {
    if (throwable instanceof InvocationTargetException) throwable = throwable.getCause();
    if (throwable instanceof RuntimeException) return (RuntimeException)throwable;
    return new RuntimeException(throwable);
  }

  public void customizeForResolve(MavenConsole console, MavenProgressIndicator process) {
    doCustomize(null, false, console, process);
  }

  public void customizeForResolve(Map<MavenId, VirtualFile> projectIdToFileMap, MavenConsole console, MavenProgressIndicator process) {
    doCustomize(projectIdToFileMap, false, console, process);
  }

  public void customizeForStrictResolve(Map<MavenId, VirtualFile> projectIdToFileMap,
                                        MavenConsole console,
                                        MavenProgressIndicator process) {
    doCustomize(projectIdToFileMap, true, console, process);
  }

  private void doCustomize(Map<MavenId, VirtualFile> projectIdToFileMap,
                           boolean strict,
                           MavenConsole console,
                           MavenProgressIndicator process) {
    ((CustomArtifactFactory)getComponent(ArtifactFactory.class)).customize();
    ((CustomArtifactFactory)getComponent(ProjectArtifactFactory.class)).customize();
    ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).customize(projectIdToFileMap, strict);
    ((CustomWagonManager)getComponent(WagonManager.class)).customize(strict);

    setConsoleAndLogger(console, process);
  }

  private void setConsoleAndLogger(MavenConsole console, MavenProgressIndicator process) {
    myCurrentIndicator = process;
    myLogger.setConsole(console);

    WagonManager wagon = getComponent(WagonManager.class);
    wagon.setDownloadMonitor(process == null ? null : new TransferListenerAdapter(process));
  }

  public void reset() {
    setConsoleAndLogger(null, null);

    ((CustomArtifactFactory)getComponent(ArtifactFactory.class)).reset();
    ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).reset();
    ((CustomWagonManager)getComponent(WagonManager.class)).reset();
  }

  public void clearCaches() {
    withProjectCachesDo(new Function<Map, Object>() {
      public Object fun(Map map) {
        map.clear();
        return null;
      }
    });
  }

  public void clearCachesFor(final org.jetbrains.idea.maven.project.MavenProject mavenProject) {
    withProjectCachesDo(new Function<Map, Object>() {
      public Object fun(Map map) {
        map.remove(mavenProject.getMavenId().getKey());
        return null;
      }
    });
  }

  public static Model interpolate(Model model, File basedir) {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties context = MavenEmbedderFactory.collectSystemProperties();

      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(context);
      model = interpolator.interpolate(ModelUtils.cloneModel(model), basedir, config, false);
    }
    catch (ModelInterpolationException e) {
      MavenLog.LOG.warn(e);
    }
    catch (InitializationException e) {
      MavenLog.LOG.error(e);
    }

    return model;
  }

  public static void alignModel(Model model, File basedir) {
    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(model, basedir);
    Build build = model.getBuild();
    build.setScriptSourceDirectory(pathTranslator.alignToBaseDirectory(build.getScriptSourceDirectory(), basedir));
  }

  private void withProjectCachesDo(Function<Map, ?> func) {
    MavenProjectBuilder builder = getComponent(MavenProjectBuilder.class);
    Field field;
    try {
      field = builder.getClass().getDeclaredField("rawProjectCache");
      field.setAccessible(true);
      func.fun(((Map)field.get(builder)));

      field = builder.getClass().getDeclaredField("processedProjectCache");
      field.setAccessible(true);
      func.fun(((Map)field.get(builder)));
    }
    catch (NoSuchFieldException e) {
      MavenLog.LOG.error(e);
    }
    catch (IllegalAccessException e) {
      MavenLog.LOG.error(e);
    }
  }

  private void configureContainer() {
    setImplementation(ArtifactFactory.class, CustomArtifactFactory.class);
    setImplementation(ProjectArtifactFactory.class, CustomArtifactFactory.class);
    setImplementation(ArtifactResolver.class, CustomArtifactResolver.class);
    setImplementation(WagonManager.class, CustomWagonManager.class);
    setImplementation(ModelInterpolator.class, CustomModelInterpolator.class);
  }

  private <T> void setImplementation(Class<T> componentClass,
                                     Class<? extends T> implementationClass) {
    ComponentDescriptor d = myContainer.getComponentDescriptor(componentClass.getName());
    d.setImplementation(implementationClass.getName());
  }
}

