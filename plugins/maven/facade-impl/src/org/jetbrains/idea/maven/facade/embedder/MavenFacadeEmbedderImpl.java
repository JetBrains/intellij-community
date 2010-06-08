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
package org.jetbrains.idea.maven.facade.embedder;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.project.*;
import org.apache.maven.project.artifact.ProjectArtifactFactory;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.injection.DefaultProfileInjector;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.facade.*;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.maven.embedder.MavenEmbedder;
import org.jetbrains.maven.embedder.MavenEmbedderSettings;
import org.jetbrains.maven.embedder.MavenExecutionResult;
import org.jetbrains.maven.embedder.PlexusComponentConfigurator;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jetbrains.idea.maven.facade.RemoteObject.wrapException;

public class MavenFacadeEmbedderImpl extends RemoteObject implements MavenFacadeEmbedder {
  private final MavenEmbedder myImpl;
  private final MavenFacadeConsoleWrapper myConsoleWrapper;
  private volatile MavenFacadeProgressIndicatorWrapper myCurrentIndicatorWrapper;

  public static MavenFacadeEmbedderImpl create(MavenFacadeSettings facadeSettings) {
    MavenEmbedderSettings settings = new MavenEmbedderSettings();

    settings.setConfigurator(new PlexusComponentConfigurator() {
      public void configureComponents(@NotNull PlexusContainer c) {
        setupContainer(c);
      }
    });
    MavenFacadeConsoleWrapper consoleWrapper = new MavenFacadeConsoleWrapper();
    consoleWrapper.setThreshold(facadeSettings.getLoggingLevel());
    settings.setLogger(consoleWrapper);
    settings.setRecursive(false);

    settings.setWorkOffline(facadeSettings.isOffline());
    settings.setUsePluginRegistry(false);

    settings.setMavenHome(facadeSettings.getMavenHome());
    settings.setUserSettingsFile(facadeSettings.getUserSettingsFile());
    settings.setGlobalSettingsFile(facadeSettings.getGlobalSettingsFile());
    settings.setLocalRepository(facadeSettings.getLocalRepository());

    settings.setSnapshotUpdatePolicy(convertUpdatePolicy(facadeSettings.getSnapshotUpdatePolicy()));
    settings.setPluginUpdatePolicy(convertUpdatePolicy(facadeSettings.getPluginUpdatePolicy()));
    settings.setProperties(MavenFacadeUtil.collectSystemProperties());

    return new MavenFacadeEmbedderImpl(MavenEmbedder.create(settings), consoleWrapper);
  }

  private static MavenEmbedderSettings.UpdatePolicy convertUpdatePolicy(MavenFacadeSettings.UpdatePolicy policy) {
    switch (policy) {
      case ALWAYS_UPDATE:
        return MavenEmbedderSettings.UpdatePolicy.ALWAYS_UPDATE;
      case DO_NOT_UPDATE:
        return MavenEmbedderSettings.UpdatePolicy.DO_NOT_UPDATE;
      default:
        MavenFacadeGlobalsManager.getLogger().error(new Throwable("unexpected update policy"));
    }
    return MavenEmbedderSettings.UpdatePolicy.DO_NOT_UPDATE;
  }

  private MavenFacadeEmbedderImpl(MavenEmbedder impl, MavenFacadeConsoleWrapper consoleWrapper) {
    myImpl = impl;
    myConsoleWrapper = consoleWrapper;
  }

  @NotNull
  public MavenWrapperExecutionResult resolveProject(@NotNull final File file,
                                                    @NotNull final Collection<String> activeProfiles)
    throws MavenFacadeProcessCanceledException {
    return doExecute(new Executor<MavenWrapperExecutionResult>() {
      public MavenWrapperExecutionResult execute() throws Exception {
        DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);
        MavenExecutionResult result = myImpl.resolveProject(file,
                                                            new ArrayList<String>(activeProfiles),
                                                            Arrays.<ResolutionListener>asList(listener));
        return createExecutionResult(file, result, listener.getRootNode());
      }
    });
  }

  @NotNull
  private MavenWrapperExecutionResult createExecutionResult(File file, MavenExecutionResult result, DependencyNode rootNode) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    THashSet<MavenId> unresolvedArtifacts = new THashSet<MavenId>();

    validate(file, result.getExceptions(), problems, unresolvedArtifacts);

    MavenProject mavenProject = result.getMavenProject();
    if (mavenProject == null) return new MavenWrapperExecutionResult(null, problems, unresolvedArtifacts);

    MavenModel model = MavenModelConverter.convertModel(mavenProject.getModel(),
                                                        mavenProject.getCompileSourceRoots(),
                                                        mavenProject.getTestCompileSourceRoots(),
                                                        mavenProject.getArtifacts(),
                                                        (rootNode == null ? Collections.emptyList() : rootNode.getChildren()),
                                                        mavenProject.getExtensionArtifacts(),
                                                        getLocalRepositoryFile());

    Collection<MavenProfile> activatedProfiles = MavenModelConverter.convertProfiles(mavenProject.getActiveProfiles());

    RemoteNativeMavenProjectHolder holder = new RemoteNativeMavenProjectHolder(mavenProject);
    try {
      UnicastRemoteObject.exportObject(holder, 0);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }

    MavenWrapperExecutionResult.ProjectData data = new MavenWrapperExecutionResult.ProjectData(
      model, MavenModelConverter.convertToMap(mavenProject.getModel()), holder, activatedProfiles);
    return new MavenWrapperExecutionResult(data, problems, unresolvedArtifacts);
  }

  @NotNull
  public MavenArtifact resolve(@NotNull final MavenArtifactInfo info,
                               @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenFacadeProcessCanceledException {
    return doExecute(new Executor<MavenArtifact>() {
      public MavenArtifact execute() throws Exception {
        return doResolve(info, remoteRepositories);
      }
    });
  }

  @NotNull
  public List<MavenArtifact> resolveTransitively(@NotNull final List<MavenArtifactInfo> artifacts,
                                                 @NotNull final List<MavenRemoteRepository> remoteRepositories) {
    try {
      Set<Artifact> toResolve = new LinkedHashSet<Artifact>();
      for (MavenArtifactInfo each : artifacts) {
        toResolve.add(createArtifact(each));
      }

      return MavenModelConverter.convertArtifacts(myImpl.resolveTransitively(toResolve, convertRepositories(remoteRepositories)),
                                                  new THashMap<Artifact, MavenArtifact>(), getLocalRepositoryFile());
    }
    catch (ArtifactResolutionException e) {
      MavenFacadeGlobalsManager.getLogger().info(e);
    }
    catch (ArtifactNotFoundException e) {
      MavenFacadeGlobalsManager.getLogger().info(e);
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));

    }
    return Collections.emptyList();
  }

  private MavenArtifact doResolve(MavenArtifactInfo info, List<MavenRemoteRepository> remoteRepositories) {
    Artifact resolved = doResolve(createArtifact(info), convertRepositories(remoteRepositories));
    return MavenModelConverter.convertArtifact(resolved, getLocalRepositoryFile());
  }

  private Artifact createArtifact(MavenArtifactInfo info) {
    return getComponent(ArtifactFactory.class).createArtifactWithClassifier(info.getGroupId(),
                                                                            info.getArtifactId(),
                                                                            info.getVersion(),
                                                                            info.getPackaging(),
                                                                            info.getClassifier());
  }

  private Artifact doResolve(Artifact artifact, List<ArtifactRepository> remoteRepositories) {
    try {
      myImpl.resolve(artifact, remoteRepositories);
      return artifact;
    }
    catch (Exception e) {
      MavenFacadeGlobalsManager.getLogger().info(e);
    }
    return artifact;
  }

  private List<ArtifactRepository> convertRepositories(List<MavenRemoteRepository> repositories) {
    List<ArtifactRepository> result = new ArrayList<ArtifactRepository>();
    for (MavenRemoteRepository each : repositories) {
      try {
        ArtifactRepositoryFactory factory = getComponent(ArtifactRepositoryFactory.class);
        result.add(ProjectUtils.buildArtifactRepository(MavenModelConverter.toNativeRepository(each), factory, getContainer()));
      }
      catch (InvalidRepositoryException e) {
        MavenFacadeGlobalsManager.getLogger().warn(e);
      }
    }
    return result;
  }

  public Collection<MavenArtifact> resolvePlugin(@NotNull final MavenPlugin plugin,
                                                 @NotNull final List<MavenRemoteRepository> repositories,
                                                 final int nativeMavenProjectId,
                                                 final boolean transitive) throws MavenFacadeProcessCanceledException {
    return doExecute(new Executor<Collection<MavenArtifact>>() {
      public Collection<MavenArtifact> execute() throws Exception {
        try {
          Plugin mavenPlugin = new Plugin();
          mavenPlugin.setGroupId(plugin.getGroupId());
          mavenPlugin.setArtifactId(plugin.getArtifactId());
          mavenPlugin.setVersion(plugin.getVersion());
          MavenProject project = RemoteNativeMavenProjectHolder.findprojectById(nativeMavenProjectId);
          PluginDescriptor result = getComponent(PluginManager.class).verifyPlugin(mavenPlugin, project,
                                                                                   myImpl.getSettings(), myImpl.getLocalRepository());
          if (!transitive) return Collections.emptyList();

          // todo try to use parallel downloading

          Map<MavenArtifactInfo, MavenArtifact> resolvedArtifacts = new THashMap<MavenArtifactInfo, MavenArtifact>();
          for (Artifact each : (Iterable<Artifact>)result.getIntroducedDependencyArtifacts()) {
            resolveIfNecessary(new MavenArtifactInfo(each.getGroupId(), each.getArtifactId(), each.getVersion(), each.getType(), null),
                               repositories, resolvedArtifacts);
          }
          for (ComponentDependency each : (List<ComponentDependency>)result.getDependencies()) {
            resolveIfNecessary(new MavenArtifactInfo(each.getGroupId(), each.getArtifactId(), each.getVersion(), each.getType(), null),
                               repositories, resolvedArtifacts);
          }
          return new THashSet<MavenArtifact>(resolvedArtifacts.values());
        }
        catch (Exception e) {
          MavenFacadeGlobalsManager.getLogger().info(e);
          return Collections.emptyList();
        }
      }
    });
  }

  private void resolveIfNecessary(MavenArtifactInfo info,
                                  List<MavenRemoteRepository> repos,
                                  Map<MavenArtifactInfo, MavenArtifact> resolvedArtifacts) {
    if (resolvedArtifacts.containsKey(info)) return;
    resolvedArtifacts.put(info, doResolve(info, repos));
  }

  @NotNull
  public MavenWrapperExecutionResult execute(@NotNull final File file,
                                             @NotNull final Collection<String> activeProfiles,
                                             @NotNull final List<String> goals)
    throws MavenFacadeProcessCanceledException {
    return doExecute(new Executor<MavenWrapperExecutionResult>() {
      public MavenWrapperExecutionResult execute() throws Exception {
        MavenExecutionResult result = myImpl.execute(file, new ArrayList<String>(activeProfiles), goals);
        return createExecutionResult(file, result, null);
      }
    });
  }

  private void validate(File file,
                        Collection<Exception> exceptions,
                        Collection<MavenProjectProblem> problems,
                        Collection<MavenId> unresolvedArtifacts) {
    for (Exception each : exceptions) {
      MavenFacadeGlobalsManager.getLogger().info(each);

      if (each instanceof InvalidProjectModelException) {
        ModelValidationResult modelValidationResult = ((InvalidProjectModelException)each).getValidationResult();
        if (modelValidationResult != null) {
          for (Object eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), (String)eachValidationProblem));
          }
        }
        else {
          problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), each.getCause().getMessage()));
        }
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
        problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), causeMessage));
      }
      else {
        problems.add(MavenProjectProblem.createStructureProblem(file.getPath(), each.getMessage()));
      }
    }
    unresolvedArtifacts.addAll(retrieveUnresolvedArtifactIds());
  }

  private Set<MavenId> retrieveUnresolvedArtifactIds() {
    Set<MavenId> result = new THashSet<MavenId>();
    ((CustomWagonManager)getComponent(WagonManager.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    return result;
  }

  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    Model result = MavenModelConverter.toNativeModel(model);
    result = doInterpolate(result, basedir);

    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(result, basedir);

    return MavenModelConverter.convertModel(result, null);
  }

  private static Model doInterpolate(Model result, File basedir) {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenFacadeUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
      result = interpolator.interpolate(result, basedir, config, false);
    }
    catch (ModelInterpolationException e) {
      MavenFacadeGlobalsManager.getLogger().warn(e);
    }
    catch (InitializationException e) {
      MavenFacadeGlobalsManager.getLogger().error(e);
    }
    return result;
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    Model result = MavenModelConverter.toNativeModel(model);
    new DefaultModelInheritanceAssembler().assembleModelInheritance(result, MavenModelConverter.toNativeModel(parentModel));
    return MavenModelConverter.convertModel(result, null);
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       Collection<String> explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) {
    Model nativeModel = MavenModelConverter.toNativeModel(model);

    List<Profile> activatedPom = new ArrayList<Profile>();
    List<Profile> activatedExternal = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    List<Profile> rawProfiles = nativeModel.getProfiles();
    List<Profile> expandedProfilesCache = null;

    for (int i = 0; i < rawProfiles.size(); i++) {
      Profile eachRawProfile = rawProfiles.get(i);

      boolean shouldAdd = explicitProfiles.contains(eachRawProfile.getId()) || alwaysOnProfiles.contains(eachRawProfile.getId());

      Activation activation = eachRawProfile.getActivation();
      if (activation != null) {
        if (activation.isActiveByDefault()) {
          activeByDefault.add(eachRawProfile);
        }

        // expand only if necessary
        if (expandedProfilesCache == null) expandedProfilesCache = doInterpolate(nativeModel, basedir).getProfiles();
        Profile eachExpandedProfile = expandedProfilesCache.get(i);

        for (ProfileActivator eachActivator : getProfileActivators()) {
          try {
            if (eachActivator.canDetermineActivation(eachExpandedProfile) && eachActivator.isActive(eachExpandedProfile)) {
              shouldAdd = true;
              break;
            }
          }
          catch (ProfileActivationException e) {
            MavenFacadeGlobalsManager.getLogger().warn(e);
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
      new DefaultProfileInjector().inject(each, nativeModel);
    }

    return new ProfileApplicationResult(MavenModelConverter.convertModel(nativeModel, null),
                                        MavenModelConverter.convertProfiles(activatedProfiles));
  }

  private static ProfileActivator[] getProfileActivators() {
    SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenFacadeUtil.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      MavenFacadeGlobalsManager.getLogger().error(e);
      return new ProfileActivator[0];
    }

    return new ProfileActivator[]{new FileProfileActivator(),
      sysPropertyActivator,
      new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }

  @NotNull
  public File getLocalRepositoryFile() {
    return myImpl.getLocalRepositoryFile();
  }

  public <T> T getComponent(Class<T> clazz) {
    return myImpl.getComponent(clazz);
  }

  public <T> T getComponent(Class<T> clazz, String roleHint) {
    return myImpl.getComponent(clazz, roleHint);
  }

  public PlexusContainer getContainer() {
    return myImpl.getContainer();
  }

  private interface Executor<T> {
    T execute() throws Exception;
  }

  private <T> T doExecute(final Executor<T> executor) throws MavenFacadeProcessCanceledException {
    final Ref<T> result = new Ref<T>();
    final boolean[] cancelled = new boolean[1];
    final Throwable[] exception = new Throwable[1];

    Future<?> future = ExecutorManager.execute(new Runnable() {
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

    MavenFacadeProgressIndicatorWrapper indicator = myCurrentIndicatorWrapper;
    while (true) {
      if (indicator.isCanceled()) throw new MavenFacadeProcessCanceledException();

      try {
        future.get(50, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      catch (ExecutionException e) {
        throw new RuntimeException(wrapException(e.getCause()));
      }
      catch (InterruptedException e) {
        throw new MavenFacadeProcessCanceledException();
      }

      if (future.isDone()) break;
    }

    if (cancelled[0]) throw new MavenFacadeProcessCanceledException();
    if (exception[0] != null) {
      throw getRethrowable(exception[0]);
    }

    return result.get();
  }

  private RuntimeException getRethrowable(Throwable throwable) {
    if (throwable instanceof InvocationTargetException) throwable = throwable.getCause();
    return new RuntimeException(wrapException(throwable));
  }

  private static void setupContainer(PlexusContainer c) {
    MavenEmbedder.setImplementation(c, ArtifactFactory.class, CustomArtifactFactory.class);
    MavenEmbedder.setImplementation(c, ProjectArtifactFactory.class, CustomArtifactFactory.class);
    MavenEmbedder.setImplementation(c, ArtifactResolver.class, CustomArtifactResolver.class);
    MavenEmbedder.setImplementation(c, WagonManager.class, CustomWagonManager.class);
    MavenEmbedder.setImplementation(c, ModelInterpolator.class, CustomModelInterpolator.class);
  }

  public void customizeForResolve(MavenFacadeConsole console, MavenFacadeProgressIndicator process) {
    doCustomize(null, false, console, process);
  }

  public void customizeForResolve(Map<MavenId, File> projectIdToFileMap, MavenFacadeConsole console, MavenFacadeProgressIndicator process) {
    doCustomize(projectIdToFileMap, false, console, process);
  }

  public void customizeForStrictResolve(Map<MavenId, File> projectIdToFileMap,
                                        MavenFacadeConsole console,
                                        MavenFacadeProgressIndicator process) {
    doCustomize(projectIdToFileMap, true, console, process);
  }

  private void doCustomize(Map<MavenId, File> projectIdToFileMap,
                           boolean strict,
                           MavenFacadeConsole logger,
                           MavenFacadeProgressIndicator process) {
    try {
      ((CustomArtifactFactory)getComponent(ArtifactFactory.class)).customize();
      ((CustomArtifactFactory)getComponent(ProjectArtifactFactory.class)).customize();
      ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).customize(projectIdToFileMap, strict);
      ((CustomWagonManager)getComponent(WagonManager.class)).customize(strict);

      setConsoleAndLogger(logger, process);
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  private void setConsoleAndLogger(MavenFacadeConsole logger, MavenFacadeProgressIndicator process) {
    myCurrentIndicatorWrapper = new MavenFacadeProgressIndicatorWrapper(process);
    myConsoleWrapper.setWrappee(logger);

    WagonManager wagon = getComponent(WagonManager.class);
    wagon.setDownloadMonitor(process == null ? null : new TransferListenerAdapter(new MavenFacadeProgressIndicatorWrapper(process)));
  }

  public void reset() {
    try {
      setConsoleAndLogger(null, null);

      ((CustomArtifactFactory)getComponent(ProjectArtifactFactory.class)).reset();
      ((CustomArtifactFactory)getComponent(ArtifactFactory.class)).reset();
      ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).reset();
      ((CustomWagonManager)getComponent(WagonManager.class)).reset();
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  public void release() {
    try {
      myImpl.release();
    }
    catch (Exception e) {
      throw new RuntimeException(wrapException(e));
    }
  }

  public void clearCaches() {
    withProjectCachesDo(new Function<Map, Object>() {
      public Object fun(Map map) {
        map.clear();
        return null;
      }
    });
  }

  public void clearCachesFor(final MavenId projectId) {
    withProjectCachesDo(new Function<Map, Object>() {
      public Object fun(Map map) {
        map.remove(projectId.getKey());
        return null;
      }
    });
  }

  private void withProjectCachesDo(Function<Map, ?> func) {
    MavenProjectBuilder builder = myImpl.getComponent(MavenProjectBuilder.class);
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
      MavenFacadeGlobalsManager.getLogger().info(e);
    }
    catch (IllegalAccessException e) {
      MavenFacadeGlobalsManager.getLogger().info(e);
    }
    catch(Exception e) {
      throw new RuntimeException(wrapException(e));
    }
  }
}

