/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.XmlInspectionGroupNames;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;

/**
 * User: anna
 * Date: 18-Nov-2005
 */
public class RequiredAttributesInspection extends XmlSuppressableInspectionTool implements XmlEntitiesInspection, UnfairLocalInspectionTool {

  public String myAdditionalRequiredHtmlAttributes = "";

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection");
  @NonNls public static final Key<InspectionProfileEntry> SHORT_NAME_KEY = Key.create(REQUIRED_ATTRIBUTES_SHORT_NAME);

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.required.attributes.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return REQUIRED_ATTRIBUTES_SHORT_NAME;
  }

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
      protected void textChanged(DocumentEvent e) {
        final Document document = e.getDocument();
        try {
          final String text = document.getText(0, document.getLength());
          if (text != null) {
            myAdditionalRequiredHtmlAttributes = text.trim();
          }
        }
        catch (BadLocationException e1) {
          RequiredAttributesInspection.LOG.error(e1);
        }
      }
    });
    additionalAttributesPanel.setText(myAdditionalRequiredHtmlAttributes);
    return panel;
  }

  public IntentionAction getIntentionAction(String name) {
    return new AddHtmlTagOrAttributeToCustomsIntention(SHORT_NAME_KEY, name, XmlBundle.message("add.optional.html.attribute", name));
  }

  @Override
  public String getAdditionalEntries() {
    return myAdditionalRequiredHtmlAttributes;
  }

  @Override
  public void addEntry(String text) {
    myAdditionalRequiredHtmlAttributes = appendName(getAdditionalEntries(), text);
  }

  private static String appendName(String toAppend, String text) {
    if (toAppend.length() > 0) {
      toAppend += "," + text;
    }
    else {
      toAppend = text;
    }
    return toAppend;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
