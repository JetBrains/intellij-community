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
package com.intellij.ide.browsers.firefox;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class FirefoxSettingsConfigurable implements Configurable {
  private static final FileChooserDescriptor PROFILES_INI_CHOOSER_DESCRIPTOR = createProfilesIniChooserDescriptor();

  private JPanel myMainPanel;
  private JComboBox myProfileCombobox;
  private TextFieldWithBrowseButton myProfilesIniPathField;
  private final FirefoxSettings mySettings;
  private String myLastProfilesIniPath;
  private String myDefaultProfilesIniPath;
  private String myDefaultProfile;

  public FirefoxSettingsConfigurable(FirefoxSettings settings) {
    mySettings = settings;
    myProfilesIniPathField.addBrowseFolderListener(XmlBundle.message("chooser.title.select.profiles.ini.file"), null, null, PROFILES_INI_CHOOSER_DESCRIPTOR);
    myProfilesIniPathField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateProfilesList();
      }
    });
  }

  public static FileChooserDescriptor createProfilesIniChooserDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!file.isDirectory() && !file.getName().equals(FirefoxUtil.PROFILES_INI_FILE)) {
          return false;
        }
        return super.isFileVisible(file, showHiddenFiles);
      }
    };
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    return !Comparing.equal(mySettings.getProfile(), getConfiguredProfileName()) ||
           !Comparing.equal(mySettings.getProfilesIniPath(), getConfiguredProfileIniPath());
  }

  @Nullable
  private String getConfiguredProfileIniPath() {
    final String path = myProfilesIniPathField.getText();
    if (myDefaultProfilesIniPath.equals(path)) {
      return null;
    }
    return FileUtil.toSystemIndependentName(path);
  }

  @Nullable
  private String getConfiguredProfileName() {
    final String selected = (String)myProfileCombobox.getSelectedItem();
    if (Comparing.equal(myDefaultProfile, selected)) {
      return null;
    }
    return selected;
  }

  public void apply() throws ConfigurationException {
    mySettings.setProfile(getConfiguredProfileName());
    mySettings.setProfilesIniPath(getConfiguredProfileIniPath());
  }

  public void reset() {
    final File defaultFile = FirefoxUtil.getDefaultProfileIniPath();
    myDefaultProfilesIniPath = defaultFile != null ? defaultFile.getAbsolutePath() : "";

    final String path = mySettings.getProfilesIniPath();
    myProfilesIniPathField.setText(path != null ? FileUtil.toSystemDependentName(path) : myDefaultProfilesIniPath);
    updateProfilesList();
    final String profileName = mySettings.getProfile();
    myProfileCombobox.setSelectedItem(profileName != null ? profileName : myDefaultProfile);
  }

  private void updateProfilesList() {
    final String profilesIniPath = myProfilesIniPathField.getText();
    if (myLastProfilesIniPath != null && myLastProfilesIniPath.equals(profilesIniPath)) return;

    myProfileCombobox.removeAllItems();
    final List<FirefoxProfile> profiles = FirefoxUtil.computeProfiles(new File(profilesIniPath));
    final FirefoxProfile defaultProfile = FirefoxUtil.getDefaultProfile(profiles);
    myDefaultProfile = defaultProfile != null ? defaultProfile.getName() : null;
    for (FirefoxProfile profile : profiles) {
      myProfileCombobox.addItem(profile.getName());
    }
    if (!profiles.isEmpty()) {
      myProfileCombobox.setSelectedIndex(0);
    }
    myLastProfilesIniPath = profilesIniPath;
  }

  public void disposeUIResources() {
  }

  @Nls
  public String getDisplayName() {
    return XmlBundle.message("display.name.firefox.settings");
  }

  public String getHelpTopic() {
    return null;
  }
}
