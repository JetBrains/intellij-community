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

import com.intellij.util.SystemProperties;
import gnu.trove.THashSet;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.logging.BaseLoggerManager;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator;
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

    Class cliRequestClass = null;
    try {
      cliRequestClass = MavenCli.class.getClassLoader().loadClass("org.apache.maven.cli.MavenCli$CliRequest");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("Class \"org.apache.maven.cli.MavenCli$CliRequest\" not found");
    }

    Object cliRequest;
    try {
      String[] commandLineOptions = new String[settings.getUserProperties().size()];
      int idx = 0;
      for (Map.Entry<Object, Object> each : settings.getUserProperties().entrySet()) {
        commandLineOptions[idx++] = "-D" + each.getKey() + "=" + each.getValue();
      }

      Constructor constructor = cliRequestClass.getDeclaredConstructor(String[].class, ClassWorld.class);
      constructor.setAccessible(true);
      cliRequest = constructor.newInstance(commandLineOptions, classWorld);

      for (String each : new String[]{"initialize", "cli", "properties", "container"}) {
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
    myContainer = FieldAccessor.get(MavenCli.class, cli, "container");
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
      result.setLocalRepository(new File(SystemProperties.getUserHome(), ".m2/repository").getPath());
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
    //DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener(myConsoleWrapper);
    //
    //MavenExecutionResult result = myImpl.resolveProject(file,
    //                                                    new ArrayList<String>(activeProfiles),
    //                                                    Arrays.<ResolutionListener>asList(listener));
    //return createExecutionResult(file, result, listener.getRootNode());

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

  public void clearCaches() throws RemoteException {
    // do nothing
  }

  public void clearCachesFor(final MavenId projectId) throws RemoteException {
    // do nothing
  }

  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) throws RemoteException {
    Model result = MavenModelConverter.toNativeModel(model);
    result = doInterpolate(result, basedir);

    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(result, basedir);

    return MavenModelConverter.convertModel(result, null);
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    throw new UnsupportedOperationException();
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       Collection<String> explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) throws RemoteException {
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
                                        collectProfilesIds(activatedProfiles));
  }

  private static Model doInterpolate(Model result, File basedir) throws RemoteException {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomMaven3ModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenServerUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
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

    return new ProfileActivator[]{new MyFileProfileActivator(basedir),
      sysPropertyActivator,
      new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }


}

