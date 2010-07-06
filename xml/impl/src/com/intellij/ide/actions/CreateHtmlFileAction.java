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

import com.intellij.CommonBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateHtmlFileAction extends CreateTemplateAction<PsiFile> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateHtmlFileAction");

  @NonNls private static final String DEFAULT_HTML_TEMPLATE_PROPERTY = "DefaultHtmlFileTemplate";

  public CreateHtmlFileAction() {
    super(XmlBundle.message("new.html.file.action"), XmlBundle.message("new.html.file.action.description"), StdFileTypes.HTML.getIcon());
  }

  @Override
  protected PsiFile createFile(String name, String templateName, PsiDirectory dir) {
    PropertiesComponent.getInstance(dir.getProject()).setValue(DEFAULT_HTML_TEMPLATE_PROPERTY, templateName);

    final FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);

    PsiElement element;
    try {
      element = FileTemplateUtil
        .createFromTemplate(template, name, FileTemplateManager.getInstance().getDefaultProperties(), dir);
      final PsiFile psiFile = element.getContainingFile();

      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        FileEditorManager.getInstance(dir.getProject()).openFile(virtualFile, true);
        return psiFile;
      }
    }
    catch (IncorrectOperationException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }

    return null;
  }

  @Override
  protected void checkBeforeCreate(String name, String templateName, PsiDirectory dir) {
    dir.checkCreateFile(name);
  }

  @NotNull
  @Override
  protected CreateFileFromTemplateDialog.Builder buildDialog(Project project, PsiDirectory directory) {
    final CreateFileFromTemplateDialog.Builder builder =
      CreateFileFromTemplateDialog.createDialog(project, XmlBundle.message("new.html.file.action"));
    builder.addKind("HTML file", StdFileTypes.HTML.getIcon(), FileTemplateManager.INTERNAL_HTML_TEMPLATE_NAME);
    builder.addKind("HTML5 file", StdFileTypes.HTML.getIcon(), FileTemplateManager.INTERNAL_HTML5_TEMPLATE_NAME);
    builder.addKind("XHTML file", StdFileTypes.HTML.getIcon(), FileTemplateManager.INTERNAL_XHTML_TEMPLATE_NAME);
    return builder;
  }

  @Override
  protected String getDefaultTempalteName(@NotNull PsiDirectory dir) {
    return PropertiesComponent.getInstance(dir.getProject()).getValue(DEFAULT_HTML_TEMPLATE_PROPERTY);
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    return XmlBundle.message("new.html.file.action");
  }

  @Override
  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }
}
