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

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
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

/**
 * @author spleaner
 */
public class HtmlUnknownTagInspection extends HtmlUnknownTagInspectionBase {

  public HtmlUnknownTagInspection() {
    super();
  }

  protected HtmlUnknownTagInspection(@NonNls @NotNull final String defaultValues) {
    super(defaultValues);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return createOptionsPanel(this);
  }

  @NotNull
  protected static JComponent createOptionsPanel(@NotNull final HtmlUnknownTagInspectionBase inspection) {
    final JPanel result = new JPanel(new BorderLayout());

    final JPanel internalPanel = new JPanel(new BorderLayout());
    result.add(internalPanel, BorderLayout.NORTH);

    final FieldPanel additionalAttributesPanel = new FieldPanel(null, inspection.getPanelTitle(), null, null);
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final Document document = e.getDocument();
        try {
          final String text = document.getText(0, document.getLength());
          if (text != null) {
            inspection.myValues = reparseProperties(text.trim());
          }
        }
        catch (BadLocationException e1) {
          inspection.getLogger().error(e1);
        }
      }
    });

    final JCheckBox checkBox = new JCheckBox(inspection.getCheckboxTitle());
    checkBox.setSelected(inspection.myCustomValuesEnabled);
    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean b = checkBox.isSelected();
        if (b != inspection.myCustomValuesEnabled) {
          inspection.myCustomValuesEnabled = b;
          additionalAttributesPanel.setEnabled(inspection.myCustomValuesEnabled);
        }
      }
    });

    internalPanel.add(checkBox, BorderLayout.NORTH);
    internalPanel.add(additionalAttributesPanel, BorderLayout.CENTER);

    additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
    additionalAttributesPanel.setEnabled(inspection.myCustomValuesEnabled);
    additionalAttributesPanel.setText(inspection.createPropertiesString());

    return result;
  }
}
