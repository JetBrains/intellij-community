// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

public final class MavenRedundantGroupIdInspection extends XmlSuppressableInspectionTool {

  @Override
  public @NotNull String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  public @NotNull String getShortName() {
    return "MavenRedundantGroupId";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof XmlFile && file.isPhysical()) {
      DomFileElement<MavenDomProjectModel> model =
        DomManager.getDomManager(file.getProject()).getFileElement((XmlFile)file, MavenDomProjectModel.class);

      if (model != null) {
        MavenDomProjectModel projectModel = model.getRootElement();

        String groupId = projectModel.getGroupId().getStringValue();
        if (groupId != null && !groupId.isEmpty()) {
          MavenDomParent parent = projectModel.getMavenParent();

          String parentGroupId = parent.getGroupId().getStringValue();

          if (groupId.equals(parentGroupId)) {
            XmlTag xmlTag = projectModel.getGroupId().getXmlTag();

            LocalQuickFix fix = new LocalQuickFix() {
              @Override
              public @IntentionFamilyName @NotNull String getFamilyName() {
                return MavenDomBundle.message("inspection.redundant.groupId.fix");
              }

              @Override
              public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                descriptor.getPsiElement().delete();
              }
            };

            return new ProblemDescriptor[]{
              manager.createProblemDescriptor(xmlTag,
                                              MavenDomBundle.message("inspection.redundant.groupId.fix.description"),
                                              fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
            };
          }
        }

      }
    }

    return null;
  }
}
