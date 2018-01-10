/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.findInstance;

/**
 * @author ibessonov
 */
public class MavenPropertyInParentInspection extends XmlSuppressableInspectionTool {

  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.property.in.parent.name");
  }

  @NotNull
  public String getShortName() {
    return "MavenPropertyInParent";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof XmlFile && (file.isPhysical() || ApplicationManager.getApplication().isUnitTestMode())) {
      DomManager domManager = DomManager.getDomManager(file.getProject());
      DomFileElement<MavenDomProjectModel> model = domManager.getFileElement((XmlFile)file, MavenDomProjectModel.class);

      if (model != null) {
        List<ProblemDescriptor> problems = ContainerUtil.newArrayListWithCapacity(3);

        MavenDomParent mavenParent = model.getRootElement().getMavenParent();
        validate(manager, isOnTheFly, problems, mavenParent.getGroupId());
        validate(manager, isOnTheFly, problems, mavenParent.getArtifactId());
        validate(manager, isOnTheFly, problems, mavenParent.getVersion());

        if (problems.isEmpty()) return ProblemDescriptor.EMPTY_ARRAY;
        return problems.toArray(new ProblemDescriptor[problems.size()]);
      }
    }

    return null;
  }

  private static void validate(@NotNull InspectionManager manager, boolean isOnTheFly,
                               @NotNull List<ProblemDescriptor> problems, @NotNull GenericDomValue<String> domValue) {
    String unresolvedValue = domValue.getRawText();
    if (unresolvedValue != null && unresolvedValue.contains("${")) {
      LocalQuickFix fix = null;
      String resolvedValue = domValue.getStringValue();
      if (unresolvedValue.equals(resolvedValue)) {
        resolvedValue = resolveXmlElement(domValue.getXmlElement());
      }

      if (!unresolvedValue.equals(resolvedValue) && !isEmpty(resolvedValue)) {
        String finalResolvedValue = resolvedValue;
        fix = new LocalQuickFixBase(MavenDomBundle.message("refactoring.inline.property")) {
          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            ((XmlTag)descriptor.getPsiElement()).getValue().setText(finalResolvedValue);
          }
        };
      }
      XmlText[] textElements = domValue.getXmlTag().getValue().getTextElements();
      if (textElements.length > 0) {
        problems.add(manager.createProblemDescriptor(textElements[0], MavenDomBundle.message("inspection.property.in.parent.description"),
                                                     fix, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
      }
    }
  }

  @Nullable
  private static String resolveXmlElement(@Nullable XmlElement xmlElement) {
    if (xmlElement == null) return null;

    MavenPropertyPsiReference psiReference = findInstance(xmlElement.getReferences(), MavenPropertyPsiReference.class);
    if (psiReference == null) return null;

    PsiElement resolvedElement = psiReference.resolve();
    if (!(resolvedElement instanceof MavenPsiElementWrapper)) return null;

    PsiElement xmlTag = ((MavenPsiElementWrapper)resolvedElement).getWrappee();
    if (!(xmlTag instanceof XmlTag)) return null;

    return ((XmlTag)xmlTag).getValue().getTrimmedText();
  }
}
