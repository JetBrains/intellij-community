// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.ChangeTemplateDataLanguageAction;
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HtmlUnknownTagInspection extends HtmlUnknownTagInspectionBase {

  public HtmlUnknownTagInspection() {
    super();
  }

  public HtmlUnknownTagInspection(@NonNls @NotNull final String defaultValues) {
    super(defaultValues);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return createOptionsPanel(this);
  }

  @NotNull
  protected static JComponent createOptionsPanel(@NotNull final HtmlUnknownElementInspection inspection) {
    final JPanel result = new JPanel(new BorderLayout());

    final JPanel internalPanel = new JPanel(new BorderLayout());
    result.add(internalPanel, BorderLayout.NORTH);

    final ExpandableTextField additionalAttributesPanel = new ExpandableTextField(s -> reparseProperties(s),
                                                                                  strings -> StringUtil.join(strings, ","));
    additionalAttributesPanel.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        final Document document = e.getDocument();
        try {
          final String text = document.getText(0, document.getLength());
          if (text != null) {
            inspection.updateAdditionalEntries(text.trim());
          }
        }
        catch (BadLocationException e1) {
          inspection.getLogger().error(e1);
        }
      }
    });

    final JCheckBox checkBox = new JCheckBox(inspection.getCheckboxTitle());
    checkBox.setSelected(inspection.isCustomValuesEnabled());
    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean b = checkBox.isSelected();
        if (b != inspection.isCustomValuesEnabled()) {
          inspection.enableCustomValues(b);
          additionalAttributesPanel.setEnabled(inspection.isCustomValuesEnabled());
        }
      }
    });

    internalPanel.add(checkBox, BorderLayout.NORTH);
    internalPanel.add(additionalAttributesPanel, BorderLayout.CENTER);

    additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
    additionalAttributesPanel.setEnabled(inspection.isCustomValuesEnabled());
    additionalAttributesPanel.setText(inspection.getAdditionalEntries());

    return result;
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
