// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;

public class RequiredAttributesInspection extends RequiredAttributesInspectionBase {
  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    FieldPanel additionalAttributesPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.html.not.required.label.text"),
                                                          InspectionsBundle.message("inspection.javadoc.html.not.required.dialog.title"),
                                                          null, null);

    panel.add(additionalAttributesPanel, BorderLayout.NORTH);
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        final Document document = e.getDocument();
        try {
          final String text = document.getText(0, document.getLength());
          if (text != null) {
            myAdditionalRequiredHtmlAttributes = text.trim();
          }
        }
        catch (BadLocationException e1) {
          LOG.error(e1);
        }
      }
    });
    additionalAttributesPanel.setText(myAdditionalRequiredHtmlAttributes);
    return panel;
  }

}
