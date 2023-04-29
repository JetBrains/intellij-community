// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.server.MavenServerConfigUtil;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerUtil;

import java.io.File;
import java.util.*;

public final class Maven40ProfileUtil {
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
  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    Model result = Maven40ModelConverter.toNativeModel(model);
    DefaultPathTranslator pathTranslator = new DefaultPathTranslator();
    DefaultUrlNormalizer urlNormalizer = new DefaultUrlNormalizer();
    StringVisitorModelInterpolator interpolator = new StringVisitorModelInterpolator(pathTranslator, urlNormalizer);
    result = doInterpolate(interpolator, result, basedir);
    //pathTranslator.alignToBaseDirectory(result, basedir);
    return Maven40ModelConverter.convertModel(result, null);
  }

  private static Model doInterpolate(StringVisitorModelInterpolator interpolator, @NotNull Model result, File basedir) {
    try {
      Properties userProperties = new Properties();
      userProperties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigProperties(basedir));
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
}

