package com.intellij.ide.browsers.firefox;

import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class FirefoxSettings extends BrowserSpecificSettings {
  private String myProfilesIniPath;
  private String myProfile;

  public FirefoxSettings() {
  }

  public FirefoxSettings(String profilesIniPath, String profile) {
    myProfilesIniPath = profilesIniPath;
    myProfile = profile;
  }

  @Nullable
  @Tag("profiles-ini-path")
  public String getProfilesIniPath() {
    return myProfilesIniPath;
  }

  public void setProfilesIniPath(String profilesIniPath) {
    myProfilesIniPath = profilesIniPath;
  }

  @Nullable
  @Tag("profile")
  public String getProfile() {
    return myProfile;
  }

  public void setProfile(String profile) {
    myProfile = profile;
  }

  @Override
  public Configurable createConfigurable() {
    return new FirefoxSettingsConfigurable(this);
  }

  @Nullable
  public File getProfilesIniFile() {
    if (myProfilesIniPath != null) {
      return new File(FileUtil.toSystemDependentName(myProfilesIniPath));
    }
    return FirefoxUtil.getDefaultProfileIniPath();
  }

  @NotNull
  @Override
  public String[] getAdditionalParameters() {
    final List<FirefoxProfile> profiles = FirefoxUtil.computeProfiles(getProfilesIniFile());
    if (profiles.size() >= 2) {
      final FirefoxProfile profile = FirefoxUtil.findProfileByNameOrDefault(myProfile, profiles);
      if (profile != null) {
        return new String[] {"-P", profile.getName()};
      }
    }
    return super.getAdditionalParameters();
  }
}
