/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author spleaner
 */
public class AddCutomTagOrAttributeIntentionAction implements LocalQuickFix {
  private String myName;
  private int myType;
  private String myInspectionName;

  public AddCutomTagOrAttributeIntentionAction(String shortName, String name, int type) {
    myInspectionName = shortName;
    myName = name;
    myType = type;
  }

  @NotNull
  public String getName() {
    if (myType == XmlEntitiesInspection.UNKNOWN_TAG) {
      return QuickFixBundle.message("add.custom.html.tag", myName);
    }

    if (myType == XmlEntitiesInspection.UNKNOWN_ATTRIBUTE) {
      return QuickFixBundle.message("add.custom.html.attribute", myName);
    }

    if (myType == XmlEntitiesInspection.NOT_REQUIRED_ATTRIBUTE) {
      return QuickFixBundle.message("add.optional.html.attribute", myName);
    }

    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.html.family");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiFile file = element.getContainingFile();

    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfile inspectionProfile = profileManager.getInspectionProfile(file);
    final ModifiableModel model = inspectionProfile.getModifiableModel();
    final LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)model.getInspectionTool(myInspectionName);
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
