/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.application.options;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeStyleHtmlPanel extends CodeStyleAbstractPanel {

  private JTextField myKeepBlankLines;
  private JComboBox myWrapAttributes;
  private JCheckBox myAlignAttributes;
  private JCheckBox myKeepWhiteSpaces;

  private JPanel myPanel;
  private JPanel myPreviewPanel;

  private JCheckBox mySpacesAroundEquality;
  private JCheckBox mySpacesAroundTagName;
  private JCheckBox myAlignText;
  private JComboBox myTextWrapping;
  private JTextField myInsertNewLineTagNames;
  private JTextField myRemoveNewLineTagNames;
  private JTextField myDoNotAlignChildrenTagNames;
  private JTextField myKeepWhiteSpacesTagNames;
  private JTextField myTextElementsTagNames;
  private JTextField myDoNotAlignChildrenMinSize;
  private JCheckBox myShouldKeepBlankLines;

  public CodeStyleHtmlPanel(CodeStyleSettings settings) {
    super(settings);
    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

    fillWrappingCombo(myWrapAttributes);
    fillWrappingCombo(myTextWrapping);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updatePreview();
      }
    };

    myKeepBlankLines.addActionListener(actionListener);
    myWrapAttributes.addActionListener(actionListener);
    myTextWrapping.addActionListener(actionListener);
    myKeepWhiteSpaces.addActionListener(actionListener);
    myAlignAttributes.addActionListener(actionListener);
    mySpacesAroundEquality.addActionListener(actionListener);
    mySpacesAroundTagName.addActionListener(actionListener);
    myAlignText.addActionListener(actionListener);

    final DocumentListener documentListener = new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        updatePreview();
      }

      public void insertUpdate(DocumentEvent e) {
        updatePreview();
      }

      public void removeUpdate(DocumentEvent e) {
        updatePreview();
      }
    };

    myKeepBlankLines.getDocument().addDocumentListener(documentListener);

    myInsertNewLineTagNames.getDocument().addDocumentListener(documentListener);
    myRemoveNewLineTagNames.getDocument().addDocumentListener(documentListener);
    myDoNotAlignChildrenTagNames.getDocument().addDocumentListener(documentListener);
    myDoNotAlignChildrenMinSize.getDocument().addDocumentListener(documentListener);
    myTextElementsTagNames.getDocument().addDocumentListener(documentListener);
    myKeepWhiteSpacesTagNames.getDocument().addDocumentListener(documentListener);
    myShouldKeepBlankLines.addActionListener(actionListener);

    myShouldKeepBlankLines.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myKeepBlankLines.setEnabled(myShouldKeepBlankLines.isSelected());
      }
    });
  }

  protected int getRightMargin() {
    return 60;
  }

  public void apply(CodeStyleSettings settings) {
    settings.HTML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    settings.HTML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    settings.HTML_TEXT_WRAP = ourWrappings[myTextWrapping.getSelectedIndex()];
    settings.HTML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.HTML_ALIGN_TEXT = myAlignText.isSelected();
    settings.HTML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.HTML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();

    settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = myInsertNewLineTagNames.getText();
    settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = myRemoveNewLineTagNames.getText();
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF = myDoNotAlignChildrenTagNames.getText();
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_SIZE = getIntValue(myDoNotAlignChildrenMinSize);
    settings.HTML_TEXT_ELEMENTS = myTextElementsTagNames.getText();
    settings.HTML_KEEP_WHITESPACES_INSIDE = myKeepWhiteSpacesTagNames.getText();
    settings.HTML_KEEP_LINE_BREAKS = myShouldKeepBlankLines.isSelected();
  }

  private int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  protected void resetImpl() {
    myKeepBlankLines.setText(String.valueOf(mySettings.HTML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(mySettings.HTML_ATTRIBUTE_WRAP));
    myTextWrapping.setSelectedIndex(getIndexForWrapping(mySettings.HTML_TEXT_WRAP));
    myAlignAttributes.setSelected(mySettings.HTML_ALIGN_ATTRIBUTES);
    myAlignText.setSelected(mySettings.HTML_ALIGN_TEXT);
    myKeepWhiteSpaces.setSelected(mySettings.HTML_KEEP_WHITESPACES);
    mySpacesAroundTagName.setSelected(mySettings.HTML_SPACE_AROUND_TAG_NAME);
    mySpacesAroundEquality.setSelected(mySettings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE);
    myShouldKeepBlankLines.setSelected(mySettings.HTML_KEEP_LINE_BREAKS);

    myInsertNewLineTagNames.setText(mySettings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
    myRemoveNewLineTagNames.setText(mySettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
    myDoNotAlignChildrenTagNames.setText(mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF);
    myDoNotAlignChildrenMinSize.setText(String.valueOf(mySettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_SIZE));
    myTextElementsTagNames.setText(mySettings.HTML_TEXT_ELEMENTS);
    myKeepWhiteSpacesTagNames.setText(mySettings.HTML_KEEP_WHITESPACES_INSIDE);

    myKeepBlankLines.setEnabled(myShouldKeepBlankLines.isSelected());
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (settings.HTML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (settings.HTML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }

    if (settings.HTML_TEXT_WRAP != ourWrappings[myTextWrapping.getSelectedIndex()]) {
      return true;
    }

    if (settings.HTML_ALIGN_ATTRIBUTES != myAlignAttributes.isSelected()) {
      return true;
    }

    if (settings.HTML_ALIGN_TEXT != myAlignText.isSelected()) {
      return true;
    }

    if (settings.HTML_KEEP_WHITESPACES != myKeepWhiteSpaces.isSelected()) {
      return true;
    }

    if (settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (settings.HTML_SPACE_AROUND_TAG_NAME != mySpacesAroundTagName.isSelected()){
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE, myInsertNewLineTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE, myRemoveNewLineTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_DO_NOT_ALIGN_CHILDREN_OF, myDoNotAlignChildrenTagNames.getText().trim())){
      return true;
    }

    if (settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_SIZE != getIntValue(myDoNotAlignChildrenMinSize)){
      return true;
    }

    if (!Comparing.equal(settings.HTML_TEXT_ELEMENTS, myTextElementsTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_KEEP_WHITESPACES_INSIDE, myKeepWhiteSpacesTagNames.getText().trim())){
      return true;
    }

    if (myShouldKeepBlankLines.isSelected() != mySettings.HTML_KEEP_LINE_BREAKS) {
      return true;
    }

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return readFromFile("preview.html");

  }

  protected FileType getFileType() {
    return StdFileTypes.HTML;
  }
}
