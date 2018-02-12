/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.ChangeTemplateDataLanguageAction;
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.PlatformIcons;
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

    final Ref<FieldPanel> panelRef = new Ref<>();
    final FieldPanel additionalAttributesPanel = new FieldPanel(null, null, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        Messages.showTextAreaDialog(panelRef.get().getTextField(), StringUtil.wordsToBeginFromUpperCase(inspection.getPanelTitle()),
                                    inspection.getClass().getSimpleName(),
                                    s -> reparseProperties(s), strings -> StringUtil.join(strings, ",")
        );
      }
    }, null);
    ((JButton)additionalAttributesPanel.getComponent(1)).setIcon(PlatformIcons.OPEN_EDIT_DIALOG_ICON);
    panelRef.set(additionalAttributesPanel);
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
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
          return "Change template data language";
        }
      };
    }
    return null;
  }
}
