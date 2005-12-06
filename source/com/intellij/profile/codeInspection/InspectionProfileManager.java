/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.DefaultProfileManager;
import com.intellij.profile.Profile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileManager extends DefaultProfileManager implements ExportableApplicationComponent {
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls private static final String ROOT_ELEMENT_TAG = "inspections";
  @NonNls private static final String BASE_PROFILE_ATTR = "base_profile";

  @NonNls public static final String INSPECTION_DIR_NAME = "inspection";

  public static InspectionProfileManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionProfileManager.class);
  }

  @NonNls private static final String CONFIG_FILE_EXTENSION = ".xml";

  public InspectionProfileManager() {
    super(Profile.INSPECTION, new Computable<Profile>() {
      public Profile compute() {
        return InspectionProfileImpl.EMPTY_PROFILE;
      }
    });
    initProfiles();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileManager");


  public File[] getExportFiles() {
    return new File[]{getProfileDirectory(INSPECTION_DIR_NAME)};
  }

  public String getPresentableName() {
    return InspectionsBundle.message("inspection.profiles.presentable.name");
  }

  void initProfiles() {
    File dir = getProfileDirectory(INSPECTION_DIR_NAME);
    File[] files = dir.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        @NonNls final String name = pathname.getName();
        int lastExtentionIdx = name.lastIndexOf(".xml");
        return lastExtentionIdx != -1;
      }
    });

    if (files == null || files.length == 0) {
      createDefaultProfile();
      return;
    }

    try {
      for (File file : files) {
        InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file);
        profile.load();
        addProfile(profile);
      }
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public InspectionProfileImpl createDefaultProfile() {
    final InspectionProfileImpl defaultProfile;
    @NonNls final String defaultProfileName = "Default";
    defaultProfile = new InspectionProfileImpl(defaultProfileName);
    defaultProfile.setBaseProfile(InspectionProfileImpl.DEFAULT_PROFILE);
    addProfile(defaultProfile);
    return defaultProfile;
  }


  private static String getProfileName(File file) throws JDOMException, IOException {
    String name = getRootElementAttribute(file, PROFILE_NAME_TAG);
    if (name != null) return name;
    String fileName = file.getName();
    int extensionIndex = fileName.lastIndexOf(CONFIG_FILE_EXTENSION);
    return fileName.substring(0, extensionIndex);
  }

  private static String getRootElementAttribute(final File file, @NonNls String name) throws JDOMException, IOException {
    try {
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      String profileName = root.getAttributeValue(name);
      if (profileName != null) return profileName;
    }
    catch (FileNotFoundException e) {
      //ignore
    }
    return null;
  }

  private static String getBaseProfileName(File file) throws JDOMException, IOException {
    return getRootElementAttribute(file, BASE_PROFILE_ATTR);
  }

  public String getComponentName() {
    return "InspectionProfileManager";
  }


}
