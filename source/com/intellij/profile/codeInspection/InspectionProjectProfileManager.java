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

import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
public class InspectionProjectProfileManager extends DefaultProjectProfileManager implements ProjectComponent{
  private Map<String, InspectionProfileWrapper>  myName2Profile = new HashMap<String, InspectionProfileWrapper>();
  private Project myProject;

  public InspectionProjectProfileManager(final Project project) {
    super(project, Profile.INSPECTION);
    myProject = project;
  }

  public static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  public String getProfileName(PsiFile psiFile) {
    return getInspectionProfile(psiFile).getName();
  }

  public InspectionProfileImpl getInspectionProfile(@NotNull final PsiElement psiElement){
    final PsiFile psiFile = psiElement.getContainingFile();
    LOG.assertTrue(psiFile != null);

    final String profile = super.getProfileName(psiFile);
    if (profile != null) return (InspectionProfileImpl)getProfile(profile);
    return (InspectionProfileImpl)myApplicationProfileManager.getRootProfile();
  }

  public InspectionProfileWrapper getProfileWrapper(final PsiElement psiElement){
    final String profileName = getInspectionProfile(psiElement).getName();
    LOG.assertTrue(profileName != null && myName2Profile.containsKey(profileName), "Profile with name \'" + profileName + "\' wasn't loaded.");
    return myName2Profile.get(profileName);
  }

  public InspectionProfileWrapper getProfileWrapper(final String profileName){
    return myName2Profile.get(profileName);
  }

  public void updateProfile(Profile profile) {
    super.updateProfile(profile);
    initProfileWrapper(profile);
  }


  public void updateProjectProfile(Profile profile) {
    super.updateProjectProfile(profile);
    initProfileWrapper(profile);
  }

  public void deleteProfile(String name) {
    super.deleteProfile(name);
    final InspectionProfileWrapper profileWrapper = myName2Profile.remove(name);
    profileWrapper.cleanup(myProject);
  }

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
        profiles.addAll(getProfiles());
        profiles.addAll(InspectionProfileManager.getInstance().getProfiles());
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
  }
}
