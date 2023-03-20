// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.ChangeTemplateDataLanguageAction;
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.*;

public class HtmlUnknownTagInspection extends HtmlUnknownTagInspectionBase {

  public HtmlUnknownTagInspection() {
    super();
  }

  public HtmlUnknownTagInspection(@NonNls @NotNull final String defaultValues) {
    super(defaultValues);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myCustomValuesEnabled", XmlAnalysisBundle.message("html.inspections.unknown.tag.checkbox.title"),
               stringList("myValues", ""))
    );
  }

  @Nullable
  @Override
  protected LocalQuickFix createChangeTemplateDataFix(PsiFile file) {
    if (file != TemplateLanguageUtil.getTemplateFile(file)) return null;

    FileViewProvider vp = file.getViewProvider();
    if (vp instanceof ConfigurableTemplateLanguageFileViewProvider) {
      final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)vp;
      final String text =
        LangBundle.message("quickfix.change.template.data.language.text", viewProvider.getTemplateDataLanguage().getDisplayName());

      return new LocalQuickFixOnPsiElement(file) {
        @NotNull
        @Override
        public String getText() {
          return text;
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        public void invoke(@NotNull Project project,
                           @NotNull PsiFile file,
                           @NotNull PsiElement startElement,
                           @NotNull PsiElement endElement) {
          ChangeTemplateDataLanguageAction.editSettings(project, file.getVirtualFile());
        }

        @Nls
        @NotNull
        @Override
        public String getFamilyName() {
          return XmlBundle.message("change.template.data.language");
        }
      };
    }
    return null;
  }
}
