// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import java.util.Optional;

import static org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.LanguageLevelPropertyQuickFix.MAVEN_COMPILER_SOURCE;
import static org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.LanguageLevelPropertyQuickFix.MAVEN_COMPILER_TARGET;

public class TargetLevelPropertyQuickFix extends LanguageLevelQuickFix{

  public TargetLevelPropertyQuickFix(@NotNull Project project, @NotNull MavenProject mavenProject) {
    super(project, mavenProject);
  }

  @Override
  protected void runLiveTemplate(@NotNull Document document,
                                 @NotNull MavenDomProjectModel model,
                                 @NotNull LanguageLevel level) {
    XmlTag tag = model.getProperties().ensureTagExists();
    String option = JpsJavaSdkType.complianceOption(level.toJavaVersion());
    tag.findFirstSubTag(MAVEN_COMPILER_SOURCE);
    String prevTargetValue = setChildTagIfAbsent(tag, MAVEN_COMPILER_TARGET, option);
    String targetValue = Optional.ofNullable(tag.findFirstSubTag(MAVEN_COMPILER_SOURCE))
      .map(sourceTag -> sourceTag.getValue())
      .map(t -> t.getText())
      .orElse(option);

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    FileDocumentManager.getInstance().saveDocument(document);

    tag = model.getProperties().ensureTagExists();
    Template template = getTemplate(tag, prevTargetValue, targetValue);
    runTemplate(template, tag);
  }

  @Nullable
  private static Template getTemplate(XmlTag tagProperty, String prevTarget, String option) {
    XmlTag tagTarget = tagProperty.findFirstSubTag(MAVEN_COMPILER_TARGET);
    PsiElement psiTarget = getXmlTagPsiValue(tagTarget);
    if (psiTarget == null) return null;

    TemplateBuilderImpl builder = new TemplateBuilderImpl(tagProperty);
    builder.replaceElement(psiTarget, "variableTarget", getExpression(prevTarget, option), true);
    return builder.buildInlineTemplate();
  }
}
