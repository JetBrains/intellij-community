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
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public class InspectionProjectProfileManager extends DefaultProjectProfileManager implements ProjectComponent{
  public InspectionProjectProfileManager(final Project project) {
    super(project, Profile.INSPECTION);
  }

  public static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  public InspectionProfile getProfile(@NotNull final PsiElement psiElement){
    final PsiFile psiFile = psiElement.getContainingFile();
    LOG.assertTrue(psiFile != null);
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null){
      return (InspectionProfile)InspectionProfileManager.getInstance().getRootProfile();
    }
    return (InspectionProfile)InspectionProfileManager.getInstance().getProfile(getProfile(virtualFile));
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
