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
package com.intellij.application.options;

import com.intellij.ide.highlighter.XmlHighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiFile;
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
  private JCheckBox mySpacesAfterTagName;
  private JCheckBox myKeepLineBreaks;
  private JCheckBox myInEmptyTag;
  private JCheckBox myWrapText;
  private JCheckBox myKeepLineBreaksInText;
  private JComboBox myWhiteSpaceAroundCDATA;
  private JCheckBox myKeepWhitespaceInsideCDATACheckBox;

  public CodeStyleXmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);

    fillWrappingCombo(myWrapAttributes);

    addPanelToWatch(myPanel);
  }

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return XmlHighlighterFactory.createXMLHighlighter(scheme);
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
    settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = mySpacesAroundEquality.isSelected();
    settings.XML_SPACE_AFTER_TAG_NAME = mySpacesAfterTagName.isSelected();
    settings.XML_SPACE_INSIDE_EMPTY_TAG = myInEmptyTag.isSelected();
    settings.XML_WHITE_SPACE_AROUND_CDATA = myWhiteSpaceAroundCDATA.getSelectedIndex();
    settings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA = myKeepWhitespaceInsideCDATACheckBox.isSelected();
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
    mySpacesAfterTagName.setSelected(settings.XML_SPACE_AFTER_TAG_NAME);
    mySpacesAroundEquality.setSelected(settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE);
    myKeepLineBreaks.setSelected(settings.XML_KEEP_LINE_BREAKS);
    myKeepLineBreaksInText.setSelected(settings.XML_KEEP_LINE_BREAKS_IN_TEXT);
    myInEmptyTag.setSelected(settings.XML_SPACE_INSIDE_EMPTY_TAG);
    myWrapText.setSelected(wrapText(settings));
    myWhiteSpaceAroundCDATA.setSelectedIndex(settings.XML_WHITE_SPACE_AROUND_CDATA);
    myKeepWhitespaceInsideCDATACheckBox.setSelected(settings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA);
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

    if (settings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (settings.XML_SPACE_AFTER_TAG_NAME != mySpacesAfterTagName.isSelected()){
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
    if (settings.XML_WHITE_SPACE_AROUND_CDATA != myWhiteSpaceAroundCDATA.getSelectedIndex()) {
      return true;
    }
    if (settings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA != this.myKeepWhitespaceInsideCDATACheckBox.isSelected()) {
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
    return readFromFile(getClass(), "preview.xml.template");
  }

  @NotNull
  protected FileType getFileType() {
    return StdFileTypes.XML;
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    //psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }
}
