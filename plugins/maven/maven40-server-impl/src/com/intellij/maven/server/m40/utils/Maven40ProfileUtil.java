// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.model.*;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.path.PathTranslator;
import org.apache.maven.model.profile.DefaultProfileActivationContext;
import org.apache.maven.model.profile.DefaultProfileInjector;
import org.apache.maven.model.profile.activation.JdkVersionProfileActivator;
import org.apache.maven.model.profile.activation.OperatingSystemProfileActivator;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.apache.maven.model.root.DefaultRootLocator;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.MavenServerConfigUtil;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.server.ProfileApplicationResult;

import java.io.File;
import java.util.*;

public final class Maven40ProfileUtil {
  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       MavenExplicitProfiles explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) {
    Model nativeModel = Maven40ModelConverter.toNativeModel(model);

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
        if (expandedProfilesCache == null) {
          DefaultPathTranslator pathTranslator = new DefaultPathTranslator();
          DefaultUrlNormalizer urlNormalizer = new DefaultUrlNormalizer();
          DefaultRootLocator rootLocator = new DefaultRootLocator();
          StringVisitorModelInterpolator interpolator = new StringVisitorModelInterpolator(pathTranslator, urlNormalizer, rootLocator);
          expandedProfilesCache = doInterpolate(interpolator, nativeModel, basedir).getProfiles();
        }
        Profile eachExpandedProfile = expandedProfilesCache.get(i);

        ModelProblemCollector collector = new ModelProblemCollector() {
          @Override
          public void add(ModelProblemCollectorRequest request) {
          }
        };
        DefaultProfileActivationContext context = new DefaultProfileActivationContext();
        for (ProfileActivator eachActivator : getProfileActivators(basedir)) {
          try {
            if (eachActivator.isActive(eachExpandedProfile, context, collector)) {
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

    return new ProfileApplicationResult(
      Maven40ModelConverter.convertModel(nativeModel),
      new MavenExplicitProfiles(collectProfilesIds(activatedProfiles), collectProfilesIds(deactivatedProfiles))
    );
  }

  private static ProfileActivator[] getProfileActivators(File basedir) {
    PropertyProfileActivator sysPropertyActivator = new PropertyProfileActivator();
    /*
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenServerUtil.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      MavenServerGlobals.getLogger().error(e);
      return new ProfileActivator[0];
    }
    */

    return new ProfileActivator[]{
      // TODO: implement
      //new MyFileProfileActivator(basedir),
      sysPropertyActivator,
      new JdkVersionProfileActivator(),
      new OperatingSystemProfileActivator()};
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

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Collection<String> result = new HashSet<>();
    for (Profile each : profiles) {
      if (each.getId() != null) {
        result.add(each.getId());
      }
    }
    return result;
  }

  @NotNull
  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir, File pomDir) {
    Model nativeModel = Maven40ModelConverter.toNativeModel(model);
    Model result = interpolateAndAlignModel(nativeModel, basedir, pomDir);
    return Maven40ModelConverter.convertModel(result);
  }

  @NotNull
  public static Model interpolateAndAlignModel(Model nativeModel, File basedir, File pomDir) {
    DefaultPathTranslator pathTranslator = new DefaultPathTranslator();
    DefaultUrlNormalizer urlNormalizer = new DefaultUrlNormalizer();
    DefaultRootLocator rootLocator = new DefaultRootLocator();
    StringVisitorModelInterpolator interpolator = new StringVisitorModelInterpolator(pathTranslator, urlNormalizer, rootLocator);
    Model result = doInterpolate(interpolator, nativeModel, basedir);
    MyDefaultPathTranslator myPathTranslator = new MyDefaultPathTranslator(pathTranslator);
    myPathTranslator.alignToBaseDirectory(result, pomDir);
    return result;
  }

  private static Model doInterpolate(StringVisitorModelInterpolator interpolator, @NotNull Model result, File basedir) {
    try {
      Properties userProperties = new Properties();
      userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigPropertiesForBaseDir(basedir));
      ModelBuildingRequest request = new DefaultModelBuildingRequest();
      request.setUserProperties(userProperties);
      request.setSystemProperties(MavenServerUtil.collectSystemProperties());
      request.setBuildStartTime(new Date());
      request.setFileModel(result);

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

  private static class MyDefaultPathTranslator {
    private final PathTranslator myPathTranslator;

    private MyDefaultPathTranslator(PathTranslator pathTranslator) {
      myPathTranslator = pathTranslator;
    }

    private String alignToBaseDirectory(String path, File basedir) {
      return myPathTranslator.alignToBaseDirectory(path, basedir);
    }

    /**
     * adapted from {@link org.apache.maven.project.path.DefaultPathTranslator#alignToBaseDirectory(Model, File)}
     */
    private void alignToBaseDirectory(Model model, File basedir) {
      if (basedir == null) {
        return;
      }

      Build build = model.getBuild();

      if (build != null) {
        build.setDirectory(alignToBaseDirectory(build.getDirectory(), basedir));

        build.setSourceDirectory(alignToBaseDirectory(build.getSourceDirectory(), basedir));

        build.setTestSourceDirectory(alignToBaseDirectory(build.getTestSourceDirectory(), basedir));

        for (Resource resource : build.getResources()) {
          resource.setDirectory(alignToBaseDirectory(resource.getDirectory(), basedir));
        }

        for (Resource resource : build.getTestResources()) {
          resource.setDirectory(alignToBaseDirectory(resource.getDirectory(), basedir));
        }

        if (build.getFilters() != null) {
          List<String> filters = new ArrayList<>();
          for (String filter : build.getFilters()) {
            filters.add(alignToBaseDirectory(filter, basedir));
          }
          build.setFilters(filters);
        }

        build.setOutputDirectory(alignToBaseDirectory(build.getOutputDirectory(), basedir));

        build.setTestOutputDirectory(alignToBaseDirectory(build.getTestOutputDirectory(), basedir));
      }

      Reporting reporting = model.getReporting();

      if (reporting != null) {
        reporting.setOutputDirectory(alignToBaseDirectory(reporting.getOutputDirectory(), basedir));
      }
    }
  }
}

