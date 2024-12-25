// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NotNull;

public class AddCustomHtmlElementIntentionAction implements LocalQuickFix {
  private final String myName;
  private final @IntentionName String myText;
  private final @NotNull Key<HtmlUnknownElementInspection> myInspectionKey;

  public AddCustomHtmlElementIntentionAction(@NotNull Key<HtmlUnknownElementInspection> inspectionKey, String name, @IntentionName String text) {
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
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    profile.modifyToolSettings(myInspectionKey, element, tool -> tool.addEntry(myName));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return generateXmlEntitiesInspectionDiffPreview(project, myInspectionKey.toString(), myName);
  }

  public static @NotNull IntentionPreviewInfo generateXmlEntitiesInspectionDiffPreview(@NotNull Project project, @NotNull String key, @NotNull String name) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final var tool = profile.getInspectionTool(key, project);
    if (tool != null && tool.getTool() instanceof XmlEntitiesInspection inspection) {
      final String list = inspection.getAdditionalEntries();
      return new IntentionPreviewInfo.CustomDiff(UnknownFileType.INSTANCE,
                                                 list,
                                                 list.isBlank() ? name : list + "," + name);
    }
    return IntentionPreviewInfo.EMPTY;
  }
}
