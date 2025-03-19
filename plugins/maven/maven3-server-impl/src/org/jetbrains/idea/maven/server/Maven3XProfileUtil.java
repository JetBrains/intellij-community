// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.util.text.VersionComparatorUtil;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.path.PathTranslator;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.profiles.activation.JdkPrefixProfileActivator;
import org.apache.maven.profiles.activation.OperatingSystemProfileActivator;
import org.apache.maven.profiles.activation.ProfileActivator;
import org.apache.maven.profiles.activation.SystemPropertyProfileActivator;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator;
import org.jetbrains.idea.maven.server.embedder.CustomMaven3ModelInterpolator2;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public final class Maven3XProfileUtil {
  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       MavenExplicitProfiles explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) {
    Model nativeModel = Maven3ModelConverter.toNativeModel(model);

    Collection<String> enabledProfiles = explicitProfiles.getEnabledProfiles();
    Collection<String> disabledProfiles = explicitProfiles.getDisabledProfiles();
    List<Profile> activatedPom = new ArrayList<>();
    List<Profile> activatedExternal = new ArrayList<>();
    List<Profile> activeByDefault = new ArrayList<>();

    List<Profile> rawProfiles = nativeModel.getProfiles();
    List<Profile> expandedProfilesCache = null;
    List<Profile> deactivatedProfiles = new ArrayList<>();

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

    List<Profile> activatedProfiles = new ArrayList<>(activatedPom.isEmpty() ? activeByDefault : activatedPom);
    activatedProfiles.addAll(activatedExternal);

    for (Profile each : activatedProfiles) {
      new DefaultProfileInjector().injectProfile(nativeModel, each, null, null);
    }

    return new ProfileApplicationResult(Maven3ModelConverter.convertModel(nativeModel, null),
                                        new MavenExplicitProfiles(collectProfilesIds(activatedProfiles),
                                                                  collectProfilesIds(deactivatedProfiles))
    );
  }

  public static @NotNull MavenModel interpolateAndAlignModel(MavenModel model, File basedir, File pomDir) {
    Model nativeModel = Maven3ModelConverter.toNativeModel(model);
    Model result = doInterpolate(nativeModel, basedir);
    org.apache.maven.project.path.PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(result, pomDir);
    return Maven3ModelConverter.convertModel(result, null);
  }

  public static Collection<String> collectActivatedProfiles(MavenProject mavenProject) {
    // for some reason project's active profiles do not contain parent's profiles - only local and settings'.
    // parent's profiles do not contain settings' profiles.

    List<Profile> profiles = new ArrayList<>();
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

  private static @NotNull Model doInterpolate(@NotNull Model result, File basedir) {
    String mavenVersion = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION);
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

  private static @NotNull Model doInterpolate325(@NotNull Model result, File basedir) {
    try {
      AbstractStringBasedModelInterpolator interpolator = new CustomMaven3ModelInterpolator(new DefaultPathTranslator());
      interpolator.initialize();

      Properties props = MavenServerUtil.collectSystemProperties();
      ProjectBuilderConfiguration config = new DefaultProjectBuilderConfiguration().setExecutionProperties(props);
      config.setBuildStartTime(new Date());

      Properties userProperties = new Properties();
      userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigPropertiesForBaseDir(basedir));
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

  private static @NotNull Model doInterpolate330(@NotNull Model result, File basedir) {
    try {

      CustomMaven3ModelInterpolator2 interpolator = new CustomMaven3ModelInterpolator2();
      if (VersionComparatorUtil.compare(System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION), "3.8.5") >= 0) {
        try {
          Class<?> clazz = Class.forName("org.apache.maven.model.interpolation.DefaultModelVersionProcessor");
          Constructor<?> constructor = clazz.getConstructor();
          Object component = constructor.newInstance();
          Method processor = interpolator.getClass()
            .getMethod("setVersionPropertiesProcessor", Class.forName("org.apache.maven.model.interpolation.ModelVersionProcessor"));
          processor.invoke(interpolator, component);
        }
        catch (Exception e) {
          MavenServerGlobals.getLogger().error(e);
        }
      }
      //interpolator.initialize();

      Properties userProperties = new Properties();
      userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigPropertiesForBaseDir(basedir));
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

      List<ModelProblemCollectorRequest> problems = new ArrayList<>();
      result = interpolator.interpolateModel(result, basedir, request, new ModelProblemCollector() {
        @Override
        public void add(ModelProblemCollectorRequest request) {
          problems.add(request);
        }
      });

      for (ModelProblemCollectorRequest problem : problems) {
        if (problem.getException() != null) {
          MavenServerGlobals.getLogger().warn(problem.getException());
        }
      }
    }
    catch (Exception e) {
      MavenServerGlobals.getLogger().error(e);
    }
    return result;
  }

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Collection<String> result = new HashSet<>();
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
}
