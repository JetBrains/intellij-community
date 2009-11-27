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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author spleaner
 */
public class AddCustomTagOrAttributeIntentionAction implements LocalQuickFix {
  private final String myName;
  private final int myType;
  private final String myInspectionName;

  public AddCustomTagOrAttributeIntentionAction(@NotNull String shortName, String name, int type) {
    myInspectionName = shortName;
    myName = name;
    myType = type;
  }

  @NotNull
  public String getName() {
    if (myType == XmlEntitiesInspection.UNKNOWN_TAG) {
      return XmlBundle.message("add.custom.html.tag", myName);
    }

    if (myType == XmlEntitiesInspection.UNKNOWN_ATTRIBUTE) {
      return XmlBundle.message("add.custom.html.attribute", myName);
    }

    if (myType == XmlEntitiesInspection.NOT_REQUIRED_ATTRIBUTE) {
      return XmlBundle.message("add.optional.html.attribute", myName);
    }

    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return XmlBundle.message("fix.html.family");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiFile file = element.getContainingFile();

    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfile inspectionProfile = profileManager.getInspectionProfile();
    final ModifiableModel model = inspectionProfile.getModifiableModel();
    final LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)model.getInspectionTool(myInspectionName, element);
    final HtmlUnknownTagInspection inspection = (HtmlUnknownTagInspection)wrapper.getTool();
    inspection.addCustomPropertyName(myName);
    model.isProperSetting(HighlightDisplayKey.find(myInspectionName));
    try {
      model.commit();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }
  }
}
