/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateHtmlFileAction extends CreateFileFromTemplateAction implements DumbAware {

  @NonNls private static final String DEFAULT_HTML_TEMPLATE_PROPERTY = "DefaultHtmlFileTemplate";

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
