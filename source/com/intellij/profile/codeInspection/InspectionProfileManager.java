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

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.DefaultApplicationProfileManager;
import com.intellij.profile.Profile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileManager extends DefaultApplicationProfileManager implements ExportableApplicationComponent {
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls private static final String BASE_PROFILE_ATTR = "base_profile";
  private InspectionToolRegistrar myRegistrar;

  public static InspectionProfileManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionProfileManager.class);
  }

  @NonNls private static final String CONFIG_FILE_EXTENSION = ".xml";

  public InspectionProfileManager(InspectionToolRegistrar registrar) {
    super(Profile.INSPECTION,
          new Computable<Profile>() {
            public Profile compute() {
              return new InspectionProfileImpl("Default");
            }
          },
          "inspection");
    myRegistrar = registrar;
    initProfiles();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    final Collection<Profile> profiles = getProfiles().values();
    for (Profile profile : profiles) {
      ((InspectionProfile)profile).save();
    }
  }


  @NotNull
  public File[] getExportFiles() {
    return new File[]{getProfileDirectory()};
  }

  @NotNull
  public String getPresentableName() {
    return InspectionsBundle.message("inspection.profiles.presentable.name");
  }

  public void initProfiles() {
    File dir = getProfileDirectory();
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

    for (File file : files) {
      try {
        InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file, myRegistrar);
        profile.load();
        addProfile(profile);
      }
      catch (Exception e) {
        file.delete();
      }
    }
  }

  public void createDefaultProfile() {
    final InspectionProfileImpl defaultProfile;
    defaultProfile = (InspectionProfileImpl)createProfile();
    defaultProfile.setBaseProfile(InspectionProfileImpl.DEFAULT_PROFILE);
    addProfile(defaultProfile);
  }


  public Profile loadProfile(String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()){
      InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file, myRegistrar);
      profile.load();
      return profile;
    }
    return getProfile(path);
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

  @NotNull
  public String getComponentName() {
    return "InspectionProfileManager";
  }


  public void updateProfile(Profile profile) {
    super.updateProfile(profile);
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      InspectionProjectProfileManager.getInstance(project).initProfileWrapper(profile);
    }
  }
}
