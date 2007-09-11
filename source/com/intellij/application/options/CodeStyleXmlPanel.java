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

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

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
  private JCheckBox myKeepLineBreaks;
  private JCheckBox myInEmptyTag;
  private JCheckBox myWrapText;
  private JCheckBox myKeepLineBreaksInText;

  public CodeStyleXmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);

    fillWrappingCombo(myWrapAttributes);

    addPanelToWatch(myPanel);
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createXMLHighlighter(scheme);
  }

  protected int getRightMargin() {
    return 60;
  }

  public void apply(CodeStyleSettings settings) {
    settings.XML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    settings.XML_KEEP_LINE_BREAKS = myKeepLineBreaks.isSelected();
    settings.XML_KEEP_LINE_BREAKS_IN_TEXT = myKeepLineBreaksInText.isSelected();
    settings.XML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    settings.XML_TEXT_WRAP = myWrapText.isSelected() ? CodeStyleSettings.WRAP_AS_NEEDED : CodeStyleSettings.DO_NOT_WRAP;
    settings.XML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.XML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.XML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();
    settings.XML_SPACE_INSIDE_EMPTY_TAG = myInEmptyTag.isSelected();

  }

  private int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myKeepBlankLines.setText(String.valueOf(settings.XML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(settings.XML_ATTRIBUTE_WRAP));
    myAlignAttributes.setSelected(settings.XML_ALIGN_ATTRIBUTES);
    myKeepWhiteSpaces.setSelected(settings.XML_KEEP_WHITESPACES);
    mySpacesAroundTagName.setSelected(settings.XML_SPACE_AROUND_TAG_NAME);
    mySpacesAroundEquality.setSelected(settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE);
    myKeepLineBreaks.setSelected(settings.XML_KEEP_LINE_BREAKS);
    myKeepLineBreaksInText.setSelected(settings.XML_KEEP_LINE_BREAKS_IN_TEXT);
    myInEmptyTag.setSelected(settings.XML_SPACE_INSIDE_EMPTY_TAG);
    myWrapText.setSelected(wrapText(settings));
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (myWrapText.isSelected() != wrapText(settings)) {
      return true;
    }
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

    if (settings.XML_KEEP_LINE_BREAKS != myKeepLineBreaks.isSelected()) {
      return true;
    }

    if (settings.XML_KEEP_LINE_BREAKS_IN_TEXT != myKeepLineBreaksInText.isSelected()) {
      return true;
    }

    if (settings.XML_SPACE_INSIDE_EMPTY_TAG != myInEmptyTag.isSelected()){
      return true;
    }

    return false;
  }

  private boolean wrapText(final CodeStyleSettings settings) {
    return settings.XML_TEXT_WRAP == CodeStyleSettings.WRAP_AS_NEEDED;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return readFromFile("preview.xml.template");
  }

  @NotNull
  protected FileType getFileType() {
    return StdFileTypes.XML;
  }
}
