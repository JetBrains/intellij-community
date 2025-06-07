// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class AddHtmlTagOrAttributeToCustomsIntention implements LocalQuickFix {
  private final String myName;
  private final @IntentionName String myText;
  private final Key<InspectionProfileEntry> myInspectionKey;

  public AddHtmlTagOrAttributeToCustomsIntention(Key<InspectionProfileEntry> inspectionKey, String name, @IntentionName String text) {
    myInspectionKey = inspectionKey;
    myName = name;
    myText = text;
  }

  @Override
  public @NotNull String getName() {
    return myText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlAnalysisBundle.message("html.quickfix.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    profile.modifyToolSettings(myInspectionKey, descriptor.getPsiElement().getContainingFile(), entry -> {
      XmlEntitiesInspection xmlEntitiesInspection = (XmlEntitiesInspection) entry;
      xmlEntitiesInspection.addEntry(myName);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return AddCustomHtmlElementIntentionAction.generateXmlEntitiesInspectionDiffPreview(project, myInspectionKey.toString(), myName);
  }
}
