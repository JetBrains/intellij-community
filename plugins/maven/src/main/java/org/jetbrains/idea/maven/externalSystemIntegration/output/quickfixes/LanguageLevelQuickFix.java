// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collections;

public abstract class LanguageLevelQuickFix {
  protected final Project project;
  private final MavenProject mavenProject;

  public LanguageLevelQuickFix(@NotNull Project project, @NotNull MavenProject mavenProject) {
    this.project = project;
    this.mavenProject = mavenProject;
  }

  public void perform(@NotNull final LanguageLevel level) {
    final MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, mavenProject.getFile());
    if (model == null) return;

    WriteCommandAction.writeCommandAction(project, DomUtil.getFile(model)).run(() -> {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      XmlFile file = DomUtil.getFile(model);
      Document document = documentManager.getDocument(file);
      if (document == null) return;

      documentManager.commitDocument(document);

      runLiveTemplate(document, model, level);
    });
  }

  protected MavenProject getMavenProject() {
    return mavenProject;
  }

  protected abstract void runLiveTemplate(@NotNull Document document,
                                          @NotNull MavenDomProjectModel model,
                                          @NotNull LanguageLevel level);

  protected static @NotNull String setChildTagIfAbsent(@NotNull XmlTag tag, @NotNull String subTagName, @NotNull String value) {
    XmlTag subTag = tag.findFirstSubTag(subTagName);
    String prevValue = "";
    if (subTag != null && StringUtil.isEmpty(subTag.getValue().getText())) {
      subTag.getValue().setText(value);
    }
    else if (subTag != null) {
      prevValue = subTag.getValue().getText();
    }
    else {
      XmlTag subT = tag.createChildTag(subTagName, tag.getNamespace(), value, false);
      tag.addSubTag(subT, false);
    }
    return prevValue;
  }

  protected void runTemplate(@Nullable Template template, @NotNull XmlTag tagProperty) {
    if (template == null) return;
    Editor editor = CreateFromUsageBaseFix.positionCursor(project, tagProperty.getContainingFile(), tagProperty);
    if (editor == null) return;
    template.setToReformat(true);
    TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateFinishedEditing());
  }

  protected static PsiElement getXmlTagPsiValue(@Nullable XmlTag tag) {
    if (tag == null) return null;
    return ContainerUtil.find(tag.getChildren(), e -> e instanceof XmlText);
  }

  @NotNull
  protected static ConstantNode getExpression(@Nullable String prevValue, @NotNull String newValue) {
    if (StringUtil.isEmptyOrSpaces(prevValue)) {
      return new ConstantNode(newValue).withLookupStrings(newValue);
    }
    return new ConstantNode(newValue).withLookupStrings(newValue, prevValue);
  }

  private class TemplateFinishedEditing extends TemplateEditingAdapter {
    @Override
    public void templateFinished(@NotNull Template template, boolean brokenOff) {
      MavenProjectsManager.getInstance(project).forceUpdateProjects(Collections.singleton(mavenProject));
    }
  }
}
