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

import org.apache.maven.cli.MavenCli;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.logging.BaseLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.embedder.FieldAccessor;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.*;

public class Maven3ServerEmbedderImpl extends MavenRemoteObject implements MavenServerEmbedder {
  @NotNull private final DefaultPlexusContainer myContainer;
  @NotNull private final Settings myMavenSettings;

  public Maven3ServerEmbedderImpl(MavenServerSettings settings) throws RemoteException {
    File mavenHome = settings.getMavenHome();
    if (mavenHome != null) {
      System.setProperty("maven.home", mavenHome.getPath());
    }

    final Maven3ServerConsoleLogger logger = new Maven3ServerConsoleLogger();
    logger.setThreshold(settings.getLoggingLevel());

    ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    MavenCli cli = new MavenCli(classWorld) {
      @Override
      protected void customizeContainer(PlexusContainer container) {
        ((DefaultPlexusContainer)container).setLoggerManager(new BaseLoggerManager() {
          @Override
          protected Logger createLogger(String s) {
            return logger;
          }
        });
      }
    };

    List<String> methods = Arrays.asList("initialize",
                                         "cli",
                                         "properties",
                                         "container");

    Class cliRequestClass = null;
    Class[] classes = MavenCli.class.getDeclaredClasses();
    for (Class<?> each : classes) {
      if (each.getSimpleName().equals("CliRequest")) {
        cliRequestClass = each;
        break;
      }
    }
    if (cliRequestClass == null) throw new RuntimeException("'CliRequest' class not found among '" + Arrays.toString(classes) + "'");

    Object cliRequest;
    try {
      List<String> commandLineOptions = new ArrayList<String>(settings.getUserProperties().size());
      for (Map.Entry<Object, Object> each : settings.getUserProperties().entrySet()) {
        commandLineOptions.add("-D" + each.getKey() + "=" + each.getValue());
      }
      String[] commandLineOptionsArray = commandLineOptions.toArray(new String[commandLineOptions.size()]);

      Constructor constructor = cliRequestClass.getDeclaredConstructor(String[].class, ClassWorld.class);
      constructor.setAccessible(true);
      cliRequest = constructor.newInstance(commandLineOptionsArray, classWorld);

      for (String each : methods) {
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
    myContainer = FieldAccessor.<DefaultPlexusContainer>get(MavenCli.class, cli, "container");
    myContainer.getLoggerManager().setThreshold(settings.getLoggingLevel());

    myMavenSettings = buildSettings(FieldAccessor.<SettingsBuilder>get(MavenCli.class, cli, "settingsBuilder"),
                                    settings,
                                    FieldAccessor.<Properties>get(cliRequestClass, cliRequest, "systemProperties"),
                                    FieldAccessor.<Properties>get(cliRequestClass, cliRequest, "userProperties"));
  }

  private Settings buildSettings(SettingsBuilder builder,
                                 MavenServerSettings settings,
                                 Properties systemProperties,
                                 Properties userProperties)
    throws RemoteException {
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

    if (settings.getLocalRepository() != null) {
      result.setLocalRepository(settings.getLocalRepository().getPath());
    }

    if (result.getLocalRepository() == null) {
      result.setLocalRepository(new File(System.getProperty("user.home"), ".m2/repository").getPath());
    }

    return result;
  }

  @Override
  public void customize(@Nullable MavenWorkspaceMap workspaceMap,
                        boolean failOnUnresolvedDependency,
                        @NotNull MavenServerConsole console,
                        @NotNull MavenServerProgressIndicator indicator) throws RemoteException {
  }

  @NotNull
  @Override
  public MavenServerExecutionResult resolveProject(@NotNull File file, @NotNull Collection<String> activeProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info, @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                 @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                                 @NotNull List<MavenRemoteRepository> repositories,
                                                 int nativeMavenProjectId,
                                                 boolean transitive) throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MavenServerExecutionResult execute(@NotNull File file, @NotNull Collection<String> activeProfiles, @NotNull List<String> goals)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() throws RemoteException {
  }

  @Override
  public void release() throws RemoteException {
    myContainer.dispose();
  }

  @Override
  public void clearCaches() throws RemoteException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearCachesFor(MavenId projectId) throws RemoteException {
    throw new UnsupportedOperationException();
  }

  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    throw new UnsupportedOperationException();
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    throw new UnsupportedOperationException();
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       Collection<String> explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) {
    throw new UnsupportedOperationException();
  }
}

