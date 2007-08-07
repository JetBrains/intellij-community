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
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.profile.DefaultApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileManager extends DefaultApplicationProfileManager implements SeverityProvider, ExportableApplicationComponent, JDOMExternalizable {
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";

  private InspectionToolRegistrar myRegistrar;
  private AtomicBoolean myProfilesAreInitialized = new AtomicBoolean(false);
  private SeverityRegistrar mySeverityRegistrar;

  public static InspectionProfileManager getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionProfileManager.class);
  }

  @NonNls private static final String CONFIG_FILE_EXTENSION = ".xml";

  @SuppressWarnings({"UnusedDeclaration"})
  public InspectionProfileManager(InspectionToolRegistrar registrar, EditorColorsManager manager) {
    super(Profile.INSPECTION,
          new Computable<Profile>() {
            public Profile compute() {
              return new InspectionProfileImpl("Default");
            }
          },
          "inspection");
    myRegistrar = registrar;
    mySeverityRegistrar = new SeverityRegistrar();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    final Collection<Profile> profiles = getProfiles().values();
    for (Profile profile : profiles) {
      final InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)profile;
      if (inspectionProfile.wasInitialized()) {
        try {
          inspectionProfile.save();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
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

  public Map<String, Profile> getProfiles() {
    initProfiles();
    return super.getProfiles();
  }

  public void initProfiles() {
    if (!myProfilesAreInitialized.getAndSet(true)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      
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
          InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file, myRegistrar, this);
          profile.load();
          addProfile(profile);
        }
        catch (Exception e) {
          file.delete();
        }
      }
    }
  }

  public void createDefaultProfile() {
    final InspectionProfileImpl defaultProfile;
    defaultProfile = (InspectionProfileImpl)createProfile();
    defaultProfile.setBaseProfile(InspectionProfileImpl.getDefaultProfile());
    addProfile(defaultProfile);
  }


  public Profile loadProfile(String path) throws IOException, JDOMException {
    final File file = new File(path);
    if (file.exists()){
      InspectionProfileImpl profile = new InspectionProfileImpl(getProfileName(file), file, myRegistrar, this);
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

 /*
   @NonNls private static final String BASE_PROFILE_ATTR = "base_profile";
   private static String getBaseProfileName(File file) throws JDOMException, IOException {
    return getRootElementAttribute(file, BASE_PROFILE_ATTR);
  }*/

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

  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public SeverityRegistrar getOwnSeverityRegistrar() {
    return mySeverityRegistrar;
  }

  public void readExternal(final Element element) throws InvalidDataException {
    mySeverityRegistrar.readExternal(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    mySeverityRegistrar.writeExternal(element);
  }
}
