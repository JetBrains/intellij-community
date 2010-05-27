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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactFactory;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeResolutionListener;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.maven.embedder.MavenEmbedder;
import org.jetbrains.maven.embedder.MavenEmbedderSettings;
import org.jetbrains.maven.embedder.MavenExecutionResult;
import org.jetbrains.maven.embedder.PlexusComponentConfigurator;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MavenEmbedderWrapper {
  public static final List<String> PHASES =
    Arrays.asList("clean", "validate", "generate-sources", "process-sources", "generate-resources",
                  "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources",
                  "generate-test-resources",
                  "process-test-resources", "test-compile", "test", "prepare-package", "package", "pre-integration-test",
                  "integration-test",
                  "post-integration-test",
                  "verify", "install", "site", "deploy");
  public static final List<String> BASIC_PHASES =
    Arrays.asList("clean", "validate", "compile", "test", "package", "install", "deploy", "site");

  private final MavenEmbedder myImpl;
  private final MavenLogger myLogger;
  private volatile MavenProgressIndicator myCurrentIndicator;

  public static MavenEmbedderWrapper create(MavenGeneralSettings generalSettings) {
    MavenEmbedderSettings settings = new MavenEmbedderSettings();

    settings.setConfigurator(new PlexusComponentConfigurator() {
      public void configureComponents(@NotNull PlexusContainer c) {
        setupContainer(c);
      }
    });
    MavenLogger logger = new MavenLogger();
    logger.setThreshold(generalSettings.getLoggingLevel().getLevel());
    settings.setLogger(logger);
    settings.setRecursive(false);

    settings.setWorkOffline(generalSettings.isWorkOffline());
    settings.setUsePluginRegistry(generalSettings.isUsePluginRegistry());

    settings.setMavenHome(generalSettings.getMavenHome());
    settings.setMavenSettingsFile(generalSettings.getMavenSettingsFile());
    settings.setLocalRepository(generalSettings.getEffectiveLocalRepository().getPath());

    settings.setSnapshotUpdatePolicy(generalSettings.getSnapshotUpdatePolicy().getEmbedderPolicy());
    settings.setPluginUpdatePolicy(generalSettings.getPluginUpdatePolicy().getEmbedderPolicy());

    return new MavenEmbedderWrapper(MavenEmbedder.create(settings), logger);
  }

  private MavenEmbedderWrapper(MavenEmbedder impl, MavenLogger logger) {
    myImpl = impl;
    myLogger = logger;
  }

  public MavenWrapperResolutionResult resolveProject(@NotNull final VirtualFile file,
                                                     @NotNull final Collection<String> activeProfiles)
    throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenWrapperResolutionResult>() {
      public MavenWrapperResolutionResult execute() throws Exception {
        DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myLogger);

        MavenExecutionResult result = myImpl.resolveProject(new File(file.getPath()),
                                                            new ArrayList<String>(activeProfiles),
                                                            Arrays.<ResolutionListener>asList(listener));

        DependencyNode rootNode = listener.getRootNode();
        Collection<DependencyNode> depsTree = rootNode == null ? Collections.emptyList() : rootNode.getChildren();
        return new MavenWrapperResolutionResult(result.getMavenProject(),
                                                depsTree, retrieveUnresolvedArtifactIds(), result.getExceptions());
      }
    });
  }

  public void resolve(@NotNull final Artifact artifact, @NotNull final List<MavenRemoteRepository> remoteRepositories)
    throws MavenProcessCanceledException {
    doExecute(new Executor<Object>() {
      public Object execute() throws Exception {
        doResolve(artifact, convertRepositories(remoteRepositories));
        return null;
      }
    });
  }

  public MavenArtifact resolve(@NotNull final MavenId id,
                               @NotNull final String type,
                               @Nullable final String classifier,
                               @NotNull final List<MavenRemoteRepository> remoteRepositories)
    throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenArtifact>() {
      public MavenArtifact execute() throws Exception {
        return new MavenArtifact(doResolve(id, type, classifier, convertRepositories(remoteRepositories)), getLocalRepositoryFile());
      }
    });
  }

  private Artifact doResolve(MavenId id, String type, String classifier, List<ArtifactRepository> remoteRepositories) {
    Artifact artifact = getComponent(ArtifactFactory.class).createArtifactWithClassifier(id.getGroupId(),
                                                                                         id.getArtifactId(),
                                                                                         id.getVersion(),
                                                                                         type,
                                                                                         classifier);
    return doResolve(artifact, remoteRepositories);
  }

  private Artifact doResolve(Artifact artifact, List<ArtifactRepository> remoteRepositories) {
    try {
      myImpl.resolve(artifact, remoteRepositories);
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
            getComponent(PluginManager.class).verifyPlugin(mavenPlugin, nativeMavenProject,
                                                           myImpl.getSettings(), myImpl.getLocalRepository());
          if (!transitive) return true;

          // todo try to use parallel downloading

          List repos = nativeMavenProject.getRemoteArtifactRepositories();
          for (Artifact each : (Iterable<Artifact>)result.getIntroducedDependencyArtifacts()) {
            doResolve(new MavenId(each.getGroupId(), each.getArtifactId(), each.getVersion()), each.getType(), null, repos);
          }
          for (ComponentDependency each : (List<ComponentDependency>)result.getDependencies()) {
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
  public MavenWrapperExecutionResult execute(@NotNull final VirtualFile file,
                                             @NotNull final Collection<String> activeProfiles,
                                             @NotNull final List<String> goals)
    throws MavenProcessCanceledException {
    return doExecute(new Executor<MavenWrapperExecutionResult>() {
      public MavenWrapperExecutionResult execute() throws Exception {
        MavenExecutionResult result = myImpl.execute(new File(file.getPath()), new ArrayList<String>(activeProfiles), goals);
        return new MavenWrapperExecutionResult(result.getMavenProject(), retrieveUnresolvedArtifactIds(), result.getExceptions());
      }
    });
  }

  private Set<MavenId> retrieveUnresolvedArtifactIds() {
    Set<MavenId> result = new THashSet<MavenId>();
    ((CustomWagonManager)getComponent(WagonManager.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).getUnresolvedCollector().retrieveUnresolvedIds(result);
    return result;
  }

  public static Model interpolate(Model model, File basedir) {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties context = collectSystemProperties();

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

  private static void setupContainer(PlexusContainer c) {
    MavenEmbedder.setImplementation(c, ArtifactFactory.class, CustomArtifactFactory.class);
    MavenEmbedder.setImplementation(c, ProjectArtifactFactory.class, CustomArtifactFactory.class);
    MavenEmbedder.setImplementation(c, ArtifactResolver.class, CustomArtifactResolver.class);
    MavenEmbedder.setImplementation(c, WagonManager.class, CustomWagonManager.class);
    MavenEmbedder.setImplementation(c, ModelInterpolator.class, CustomModelInterpolator.class);
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

    ((CustomArtifactFactory)getComponent(ProjectArtifactFactory.class)).reset();
    ((CustomArtifactFactory)getComponent(ArtifactFactory.class)).reset();
    ((CustomArtifactResolver)getComponent(ArtifactResolver.class)).reset();
    ((CustomWagonManager)getComponent(WagonManager.class)).reset();
  }

  public void release() {
    myImpl.release();
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
      MavenLog.LOG.error(e);
    }
    catch (IllegalAccessException e) {
      MavenLog.LOG.error(e);
    }
  }

  public static Properties collectSystemProperties() {
    return MavenEmbedder.collectSystemProperties();
  }

  public static void resetSystemPropertiesCacheInTests() {
    MavenEmbedder.resetSystemPropertiesCache();
  }

}

