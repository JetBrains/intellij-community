// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CreateHtmlFileAction extends CreateFileFromTemplateAction implements DumbAware {

  private static final @NonNls String DEFAULT_HTML_TEMPLATE_PROPERTY = "DefaultHtmlFileTemplate";

  public CreateHtmlFileAction() {
    super(XmlBundle.messagePointer("html.action.new.file.name"), XmlBundle.messagePointer("html.action.new.file.description"),
          HtmlFileType.INSTANCE.getIcon());
  }

  @Override
  protected String getDefaultTemplateProperty() {
    return DEFAULT_HTML_TEMPLATE_PROPERTY;
  }

  @Override
  protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(XmlBundle.message("html.action.new.file.dialog.title"))
      .addKind(XmlBundle.message("html.action.new.file.item.html5.file"), HtmlFileType.INSTANCE.getIcon(), FileTemplateManager.INTERNAL_HTML5_TEMPLATE_NAME);
  }

  @Override
  protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
    return XmlBundle.message("html.action.new.file.name");
  }
}
