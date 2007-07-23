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

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
@State(
  name = "InspectionProjectProfileManager",
  storages = {
    @Storage(
      id ="default",
      file = "$PROJECT_FILE$"
    )
    ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/inspectionProfiles/", scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = InspectionProjectProfileManager.ProfileStateSplitter.class)
    }
)
public class InspectionProjectProfileManager extends DefaultProjectProfileManager implements ProjectComponent, PersistentStateComponent<Element> {
  private Map<String, InspectionProfileWrapper>  myName2Profile = new HashMap<String, InspectionProfileWrapper>();
  private Project myProject;

  @SuppressWarnings({"UnusedDeclaration"})
  public InspectionProjectProfileManager(final Project project, EditorColorsManager manager) {
    super(project, Profile.INSPECTION);
    myProject = project;
  }

  public static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  public String getProfileName(PsiFile psiFile) {
    return getInspectionProfile(psiFile).getName();
  }

  public Element getState() {
    try {
      final Element e = new Element("settings");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public @NotNull InspectionProfile getInspectionProfile(@NotNull final PsiElement psiElement){
    final PsiFile psiFile = psiElement.getContainingFile();
    LOG.assertTrue(psiFile != null);

    final HighlightingSettingsPerFile settingsPerFile = HighlightingSettingsPerFile.getInstance(myProject);

    final InspectionProfile cachedProfile = settingsPerFile.getInspectionProfile(psiFile);
    if (cachedProfile != null) {
      return cachedProfile;
    }

    InspectionProfileImpl inspectionProfile = null;

    //by name
    final String profile = super.getProfileName(psiFile);
    if (profile != null) {
      inspectionProfile = (InspectionProfileImpl)getProfile(profile);
    }

    //default
    if (inspectionProfile == null) {
      inspectionProfile = (InspectionProfileImpl)myApplicationProfileManager.getRootProfile();
    }

    settingsPerFile.addProfileSettingForFile(psiFile, inspectionProfile);

    return inspectionProfile;
  }

  public InspectionProfileWrapper getProfileWrapper(final PsiElement psiElement){
    final InspectionProfile profile = getInspectionProfile(psiElement);
    final String profileName = profile.getName();
    if (!myName2Profile.containsKey(profileName)){
      initProfileWrapper(profile);
    }
    return myName2Profile.get(profileName);
  }

  public InspectionProfileWrapper getProfileWrapper(final String profileName){
    return myName2Profile.get(profileName);
  }

  public void updateProfile(Profile profile) {
    super.updateProfile(profile);
    initProfileWrapper(profile);
  }

  public void deleteProfile(String name) {
    super.deleteProfile(name);
    final InspectionProfileWrapper profileWrapper = myName2Profile.remove(name);
    LOG.assertTrue(profileWrapper != null, "Profile wasn't initialized" + name);
    profileWrapper.cleanup(myProject);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "InspectionProjectProfileManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable(){
      public void run() {
        Set<Profile> profiles = new HashSet<Profile>();
        profiles.add(getProjectProfileImpl());
        profiles.addAll(getProfiles().values());
        profiles.addAll(InspectionProfileManager.getInstance().getProfiles().values());
        for (Profile profile : profiles) {
          initProfileWrapper(profile);
        }
      }
    });
  }

  public void initProfileWrapper(final Profile profile) {
    final InspectionProfileWrapper wrapper = new InspectionProfileWrapper((InspectionProfile)profile);
    wrapper.init(myProject);
    myName2Profile.put(profile.getName(), wrapper);
  }

  public void projectClosed() {
    for (InspectionProfileWrapper wrapper : myName2Profile.values()) {
      wrapper.cleanup(myProject);
    }
    HighlightingSettingsPerFile.getInstance(myProject).cleanProfileSettings();
  }
}
