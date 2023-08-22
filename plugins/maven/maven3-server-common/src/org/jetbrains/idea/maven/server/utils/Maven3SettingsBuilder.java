// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.utils;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerSettings;

import java.io.File;
import java.util.Properties;

public final class Maven3SettingsBuilder {
  public static Settings buildSettings(SettingsBuilder builder,
                                       MavenServerSettings settings,
                                       Properties systemProperties,
                                       Properties userProperties) {
    SettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
    if (settings.getGlobalSettingsPath() != null) {
      settingsRequest.setGlobalSettingsFile(new File(settings.getGlobalSettingsPath()));
    }
    if (settings.getUserSettingsPath() != null) {
      settingsRequest.setUserSettingsFile(new File(settings.getUserSettingsPath()));
    }

    settingsRequest.setSystemProperties(systemProperties);
    settingsRequest.setUserProperties(userProperties);

    Settings result = new Settings();
    try {
      result = builder.build(settingsRequest).getEffectiveSettings();
    }
    catch (SettingsBuildingException e) {
      MavenServerGlobals.getLogger().info(e);
    }

    result.setOffline(settings.isOffline());

    if (settings.getLocalRepositoryPath() != null) {
      result.setLocalRepository(settings.getLocalRepositoryPath());
    }

    if (result.getLocalRepository() == null) {
      result.setLocalRepository(new File(System.getProperty("user.home"), ".m2/repository").getPath());
    }

    return result;
  }
}
