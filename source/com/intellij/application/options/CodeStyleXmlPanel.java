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
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleXmlPanel extends CodeStyleAbstractPanel{
  private JTextField myKeepBlankLines;
  private JComboBox myWrapAttributes;
  private JCheckBox myAlignAttributes;
  private JCheckBox myKeepWhiteSpaces;

  private JPanel myPanel;
  private JPanel myPreviewPanel;

  private JCheckBox mySpacesAroundEquality;
  private JCheckBox mySpacesAroundTagName;

  public CodeStyleXmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);

    fillWrappingCombo(myWrapAttributes);

    addPanelToWatch(myPanel);
  }

  protected int getRightMargin() {
    return 60;
  }

  public void apply(CodeStyleSettings settings) {
    settings.XML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    settings.XML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    settings.XML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.XML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.XML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();

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
    myKeepBlankLines.setText(String.valueOf(mySettings.XML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(mySettings.XML_TEXT_WRAP));
    myAlignAttributes.setSelected(mySettings.XML_ALIGN_ATTRIBUTES);
    myKeepWhiteSpaces.setSelected(mySettings.XML_KEEP_WHITESPACES);
    mySpacesAroundTagName.setSelected(mySettings.XML_SPACE_AROUND_TAG_NAME);
    mySpacesAroundEquality.setSelected(mySettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE);
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (settings.XML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (settings.XML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }
    if (settings.XML_ALIGN_ATTRIBUTES != myAlignAttributes.isSelected()) {
      return true;
    }
    if (settings.XML_KEEP_WHITESPACES != myKeepWhiteSpaces.isSelected()) {
      return true;
    }

    if (settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (settings.XML_SPACE_AROUND_TAG_NAME != mySpacesAroundTagName.isSelected()){
      return true;
    }

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return readFromFile("preview.xml.properties");
  }

  protected FileType getFileType() {
    return StdFileTypes.XML;
  }
}
