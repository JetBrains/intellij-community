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
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public class InspectionProjectProfileManager extends DefaultProjectProfileManager implements ProjectComponent{
  private Map<VirtualFile, String> myHectorSettings = new HashMap<VirtualFile, String>();

  @NonNls public static final String HECTOR = "hector_settings";

  private InspectionProjectProfileManager myModel = null;

  public InspectionProjectProfileManager(final Project project) {
    super(project, Profile.INSPECTION);
    HighlightingSettingsPerFile settings = HighlightingSettingsPerFile.getInstance(project);
    myHectorSettings.putAll(settings.getHectorSettings());
  }

  public static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  public InspectionProfile getProfile(@NotNull final PsiElement psiElement){
    final Pair<String, Boolean> inspectionProfilePair = HighlightingSettingsPerFile.getInstance(psiElement.getProject()).getInspectionProfile(psiElement);
    if (inspectionProfilePair != null && inspectionProfilePair.second) {
      InspectionProfile inspectionProfile = (InspectionProfile)InspectionProjectProfileManager.getInstance(myProject).getProfile(inspectionProfilePair.first);
      if (inspectionProfile != null){
        return inspectionProfile;
      }
    }
    final PsiFile psiFile = psiElement.getContainingFile();
    LOG.assertTrue(psiFile != null);
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null){
      return (InspectionProfile)InspectionProfileManager.getInstance().getRootProfile();
    }
    return (InspectionProfile)InspectionProfileManager.getInstance().getProfile(getProfile(virtualFile));
  }

  public void copy(ProjectProfileManager manager) {
    super.copy(manager);
    myHectorSettings.clear();
    myHectorSettings.putAll(((InspectionProjectProfileManager)manager).myHectorSettings);
    HighlightingSettingsPerFile.getInstance(myProject).correctProfileSettings(myHectorSettings);
  }

  public boolean isModified(ProjectProfileManager manager) {
    if (super.isModified(manager)) return true;
    InspectionProjectProfileManager inspectionManager = (InspectionProjectProfileManager)manager;
    if (inspectionManager.myHectorSettings.size() != myHectorSettings.size()) return true;
    for (VirtualFile file : myHectorSettings.keySet()) {
      if (!inspectionManager.myHectorSettings.containsKey(file)) return true;
      if (!Comparing.equal(inspectionManager.myHectorSettings.get(file), myHectorSettings.get(file))) return true;
    }
    return false;
  }

  public void changeHectorSettingsForFile(VirtualFile file, String profile){
    if (!myProfiles.containsKey(profile)){
      final Profile projectProfile = myApplicationProfileManager.createProfile();
      projectProfile.copyFrom(myApplicationProfileManager.getProfile(profile));
      myProfiles.put(profile, projectProfile);
    }
    myHectorSettings.put(file, profile);
  }

  public void clearHectorAssignment(VirtualFile file){
    myHectorSettings.remove(file);
  }

  public void clearHectorAssignments() {
    myHectorSettings.clear();
  }

  public Map<VirtualFile, String> getHectorAssignments() {
    return myHectorSettings;
  }

  public ProjectProfileManager getModifiableModel() {
    if (myModel == null){
      myModel = new InspectionProjectProfileManager(myProject);
    }
    return myModel;
  }

  public void updateAdditionalSettings() {
    myHectorSettings.clear();
    HighlightingSettingsPerFile settings = HighlightingSettingsPerFile.getInstance(myProject);
    myHectorSettings.putAll(settings.getHectorSettings());
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    final Element hector = element.getChild(HECTOR);
    if (hector != null){
      HighlightingSettingsPerFile.getInstance(myProject).readHectorProfiles(hector);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (USE_PROJECT_LEVEL_SETTINGS){
      Element hector = new Element(HECTOR);
      HighlightingSettingsPerFile.getInstance(myProject).writeHectorProfiles(hector, true);
      element.addContent(hector);
    }
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
  }

  public void projectClosed() {
  }



}
