// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.RightMarginForm;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.highlighter.XmlHighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.xml.XmlUiBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class CodeStyleXmlPanel extends CodeStyleAbstractPanel{
  private JTextField myKeepBlankLines;
  private JComboBox<CodeStyleSettings.WrapStyle> myWrapAttributes;
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
  private JComboBox<String> myWhiteSpaceAroundCDATA;
  private JCheckBox myKeepWhitespaceInsideCDATACheckBox;
  private JBScrollPane myJBScrollPane;
  private JPanel myRightMarginPanel;
  private RightMarginForm myRightMarginForm;

  public CodeStyleXmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);
    fillWrappingCombo(myWrapAttributes);
    addPanelToWatch(myPanel);
    myWhiteSpaceAroundCDATA.setModel(new CollectionComboBoxModel<@Nls String>(
      Arrays.asList(XmlUiBundle.message("preserve"),
                    XmlUiBundle.message("remove.keep.with.tags"),
                    XmlUiBundle.message("add.new.lines"))));
  }

  @Override
  protected EditorHighlighter createHighlighter(final @NotNull EditorColorsScheme scheme) {
    return XmlHighlighterFactory.createXMLHighlighter(scheme);
  }

  @Override
  protected int getRightMargin() {
    return 60;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    xmlSettings.XML_KEEP_LINE_BREAKS = myKeepLineBreaks.isSelected();
    xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT = myKeepLineBreaksInText.isSelected();
    xmlSettings.XML_ATTRIBUTE_WRAP = CodeStyleSettings.WrapStyle.getId((CodeStyleSettings.WrapStyle)myWrapAttributes.getSelectedItem());
    xmlSettings.XML_TEXT_WRAP = myWrapText.isSelected() ? CommonCodeStyleSettings.WRAP_AS_NEEDED : CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    xmlSettings.XML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = mySpacesAroundEquality.isSelected();
    xmlSettings.XML_SPACE_AFTER_TAG_NAME = mySpacesAfterTagName.isSelected();
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = myInEmptyTag.isSelected();
    xmlSettings.XML_WHITE_SPACE_AROUND_CDATA = myWhiteSpaceAroundCDATA.getSelectedIndex();
    xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA = myKeepWhitespaceInsideCDATACheckBox.isSelected();
    myRightMarginForm.apply(settings);
  }

  private static int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  protected void resetImpl(final @NotNull CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    myKeepBlankLines.setText(String.valueOf(xmlSettings.XML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedItem(CodeStyleSettings.WrapStyle.forWrapping(xmlSettings.XML_ATTRIBUTE_WRAP));
    myAlignAttributes.setSelected(xmlSettings.XML_ALIGN_ATTRIBUTES);
    myKeepWhiteSpaces.setSelected(xmlSettings.XML_KEEP_WHITESPACES);
    mySpacesAfterTagName.setSelected(xmlSettings.XML_SPACE_AFTER_TAG_NAME);
    mySpacesAroundEquality.setSelected(xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE);
    myKeepLineBreaks.setSelected(xmlSettings.XML_KEEP_LINE_BREAKS);
    myKeepLineBreaksInText.setSelected(xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT);
    myInEmptyTag.setSelected(xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG);
    myWrapText.setSelected(wrapText(settings));
    myWhiteSpaceAroundCDATA.setSelectedIndex(xmlSettings.XML_WHITE_SPACE_AROUND_CDATA);
    myKeepWhitespaceInsideCDATACheckBox.setSelected(xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA);
    myRightMarginForm.reset(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    if (myWrapText.isSelected() != wrapText(settings)) {
      return true;
    }
    if (xmlSettings.XML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (xmlSettings.XML_ATTRIBUTE_WRAP !=
        CodeStyleSettings.WrapStyle.getId((CodeStyleSettings.WrapStyle)myWrapAttributes.getSelectedItem())) {
      return true;
    }
    if (xmlSettings.XML_ALIGN_ATTRIBUTES != myAlignAttributes.isSelected()) {
      return true;
    }
    if (xmlSettings.XML_KEEP_WHITESPACES != myKeepWhiteSpaces.isSelected()) {
      return true;
    }

    if (xmlSettings.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (xmlSettings.XML_SPACE_AFTER_TAG_NAME != mySpacesAfterTagName.isSelected()){
      return true;
    }

    if (xmlSettings.XML_KEEP_LINE_BREAKS != myKeepLineBreaks.isSelected()) {
      return true;
    }

    if (xmlSettings.XML_KEEP_LINE_BREAKS_IN_TEXT != myKeepLineBreaksInText.isSelected()) {
      return true;
    }

    if (xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG != myInEmptyTag.isSelected()){
      return true;
    }
    if (xmlSettings.XML_WHITE_SPACE_AROUND_CDATA != myWhiteSpaceAroundCDATA.getSelectedIndex()) {
      return true;
    }
    if (xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA != this.myKeepWhitespaceInsideCDATACheckBox.isSelected()) {
      return true;
    }

    return myRightMarginForm.isModified(settings);
  }

  private static boolean wrapText(final CodeStyleSettings settings) {
    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    return xmlSettings.XML_TEXT_WRAP == CommonCodeStyleSettings.WRAP_AS_NEEDED;
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected String getPreviewText() {
    return readFromFile(getClass(), "preview.xml.template");
  }

  @Override
  protected @NotNull FileType getFileType() {
    return XmlFileType.INSTANCE;
  }

  private void createUIComponents() {
    myJBScrollPane = new JBScrollPane() {
      @Override
      public Dimension getPreferredSize() {
        Dimension prefSize = super.getPreferredSize();
        return new Dimension(prefSize.width + 15, prefSize.height);
      }
    };
    myRightMarginForm = new RightMarginForm(XmlFileType.INSTANCE.getLanguage(), getSettings());
    myRightMarginPanel = myRightMarginForm.getTopPanel();
  }
}
