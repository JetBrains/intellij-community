package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
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

/**
 * @author Sergey Evdokimov
 */
public class MavenRedundantGroupIdInspection extends XmlSuppressableInspectionTool {

  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.redundant.groupId.name");
  }

  @NotNull
  public String getShortName() {
    return "MavenRedundantGroupId";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof XmlFile && (file.isPhysical() || ApplicationManager.getApplication().isUnitTestMode())) {
      DomFileElement<MavenDomProjectModel> model =
        DomManager.getDomManager(file.getProject()).getFileElement((XmlFile)file, MavenDomProjectModel.class);

      if (model != null) {
        MavenDomProjectModel projectModel = model.getRootElement();

        String groupId = projectModel.getGroupId().getStringValue();
        if (groupId != null && groupId.length() > 0) {
          MavenDomParent parent = projectModel.getMavenParent();

          String parentGroupId = parent.getGroupId().getStringValue();

          if (groupId.equals(parentGroupId)) {
            XmlTag xmlTag = projectModel.getGroupId().getXmlTag();

            LocalQuickFix fix = new LocalQuickFixBase("Remove unnecessary <groupId>") {
              @Override
              public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                PsiElement xmlTag = descriptor.getPsiElement();

                if (xmlTag.isValid() && FileModificationService.getInstance().preparePsiElementForWrite(xmlTag)) {
                  xmlTag.delete();
                }
              }
            };

            return new ProblemDescriptor[]{
              manager.createProblemDescriptor(xmlTag,
                                              "Definition of groupId is redundant, because it's inherited from the parent",
                                              fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
            };
          }
        }

      }
    }

    return null;
  }
}
