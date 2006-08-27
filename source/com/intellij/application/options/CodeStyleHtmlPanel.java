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
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

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
  private TextFieldWithBrowseButton myInsertNewLineTagNames;
  private TextFieldWithBrowseButton myRemoveNewLineTagNames;
  private TextFieldWithBrowseButton myDoNotAlignChildrenTagNames;
  private TextFieldWithBrowseButton myKeepWhiteSpacesTagNames;
  private TextFieldWithBrowseButton myInlineElementsTagNames;
  private JTextField myDoNotAlignChildrenMinSize;
  private JCheckBox myShouldKeepBlankLines;
  private JCheckBox mySpaceInEmptyTag;
  private JCheckBox myWrapText;
  private JCheckBox myShouldKeepLineBreaksInText;
  private TextFieldWithBrowseButton myDontBreakIfInlineContent;

  public CodeStyleHtmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);

    fillWrappingCombo(myWrapAttributes);

    customizeField(ApplicationBundle.message("title.insert.new.line.before.tags"), myInsertNewLineTagNames);
    customizeField(ApplicationBundle.message("title.remove.line.breaks.before.tags"), myRemoveNewLineTagNames);
    customizeField(ApplicationBundle.message("title.do.not.indent.children.of"), myDoNotAlignChildrenTagNames);
    customizeField(ApplicationBundle.message("title.inline.elements"), myInlineElementsTagNames);
    customizeField(ApplicationBundle.message("title.keep.whitespaces.inside"), myKeepWhiteSpacesTagNames);
    customizeField(ApplicationBundle.message("title.dont.wrap.if.inline.content"), myDontBreakIfInlineContent);

    myInsertNewLineTagNames.getTextField().setColumns(5);
    myRemoveNewLineTagNames.getTextField().setColumns(5);
    myDoNotAlignChildrenTagNames.getTextField().setColumns(5);
    myKeepWhiteSpacesTagNames.getTextField().setColumns(5);
    myInlineElementsTagNames.getTextField().setColumns(5);
    myDontBreakIfInlineContent.getTextField().setColumns(5);


    addPanelToWatch(myPanel);
  }

  protected LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createXMLHighlighter(scheme);
  }

  private static void customizeField(final String title, final TextFieldWithBrowseButton uiField) {
    uiField.getTextField().setEditable(false);
    uiField.setButtonIcon(Icons.OPEN_EDIT_DIALOG_ICON);
    uiField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final TagListDialog tagListDialog = new TagListDialog(title);
        tagListDialog.setData(createCollectionOn(uiField.getText()));
        tagListDialog.show();
        if (tagListDialog.isOK()) {
          uiField.setText(createStringOn(tagListDialog.getData()));
        }
      }

      private String createStringOn(final ArrayList<String> data) {
        return StringUtil.join(data.toArray(new String[data.size()]), ",");
      }

      private ArrayList<String> createCollectionOn(final String data) {
        if (data == null) {
          return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(data.split(",")));
      }

    });
  }

  protected int getRightMargin() {
    return 60;
  }

  public void apply(CodeStyleSettings settings) {
    settings.HTML_KEEP_BLANK_LINES = getIntValue(myKeepBlankLines);
    settings.HTML_ATTRIBUTE_WRAP = ourWrappings[myWrapAttributes.getSelectedIndex()];
    settings.HTML_TEXT_WRAP = myWrapText.isSelected() ? CodeStyleSettings.WRAP_AS_NEEDED : CodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_SPACE_INSIDE_EMPTY_TAG = mySpaceInEmptyTag.isSelected();
    settings.HTML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.HTML_ALIGN_TEXT = myAlignText.isSelected();
    settings.HTML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.HTML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();

    settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = myInsertNewLineTagNames.getText();
    settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = myRemoveNewLineTagNames.getText();
    settings.HTML_DO_NOT_INDENT_CHILDREN_OF = myDoNotAlignChildrenTagNames.getText();
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = getIntValue(myDoNotAlignChildrenMinSize);
    settings.HTML_INLINE_ELEMENTS = myInlineElementsTagNames.getText();
    settings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = myDontBreakIfInlineContent.getText();
    settings.HTML_KEEP_WHITESPACES_INSIDE = myKeepWhiteSpacesTagNames.getText();
    settings.HTML_KEEP_LINE_BREAKS = myShouldKeepBlankLines.isSelected();
    settings.HTML_KEEP_LINE_BREAKS_IN_TEXT = myShouldKeepLineBreaksInText.isSelected();
  }

  private static int getIntValue(JTextField keepBlankLines) {
    try {
      return Integer.parseInt(keepBlankLines.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myKeepBlankLines.setText(String.valueOf(settings.HTML_KEEP_BLANK_LINES));
    myWrapAttributes.setSelectedIndex(getIndexForWrapping(settings.HTML_ATTRIBUTE_WRAP));
    myWrapText.setSelected(settings.HTML_TEXT_WRAP != CodeStyleSettings.DO_NOT_WRAP);
    mySpaceInEmptyTag.setSelected(settings.HTML_SPACE_INSIDE_EMPTY_TAG);
    myAlignAttributes.setSelected(settings.HTML_ALIGN_ATTRIBUTES);
    myAlignText.setSelected(settings.HTML_ALIGN_TEXT);
    myKeepWhiteSpaces.setSelected(settings.HTML_KEEP_WHITESPACES);
    mySpacesAroundTagName.setSelected(settings.HTML_SPACE_AROUND_TAG_NAME);
    mySpacesAroundEquality.setSelected(settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE);
    myShouldKeepBlankLines.setSelected(settings.HTML_KEEP_LINE_BREAKS);
    myShouldKeepLineBreaksInText.setSelected(settings.HTML_KEEP_LINE_BREAKS_IN_TEXT);

    myInsertNewLineTagNames.setText(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
    myRemoveNewLineTagNames.setText(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
    myDoNotAlignChildrenTagNames.setText(settings.HTML_DO_NOT_INDENT_CHILDREN_OF);
    myDoNotAlignChildrenMinSize.setText(String.valueOf(settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES));
    myInlineElementsTagNames.setText(settings.HTML_INLINE_ELEMENTS);
    myDontBreakIfInlineContent.setText(settings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT);
    myKeepWhiteSpacesTagNames.setText(settings.HTML_KEEP_WHITESPACES_INSIDE);
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (settings.HTML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (settings.HTML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }

    if ((settings.HTML_TEXT_WRAP == CodeStyleSettings.WRAP_AS_NEEDED) != myWrapText.isSelected()) {
      return true;
    }

    if (settings.HTML_SPACE_INSIDE_EMPTY_TAG != mySpaceInEmptyTag.isSelected()) {
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

    if (settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE != mySpacesAroundEquality.isSelected()) {
      return true;
    }

    if (settings.HTML_SPACE_AROUND_TAG_NAME != mySpacesAroundTagName.isSelected()) {
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE, myInsertNewLineTagNames.getText().trim())) {
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE, myRemoveNewLineTagNames.getText().trim())) {
      return true;
    }

    if (!Comparing.equal(settings.HTML_DO_NOT_INDENT_CHILDREN_OF, myDoNotAlignChildrenTagNames.getText().trim())) {
      return true;
    }

    if (settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES != getIntValue(myDoNotAlignChildrenMinSize)) {
      return true;
    }

    if (!Comparing.equal(settings.HTML_INLINE_ELEMENTS, myInlineElementsTagNames.getText().trim())) return true;
    if (!Comparing.equal(settings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT, myDontBreakIfInlineContent.getText().trim())) return true;

    if (!Comparing.equal(settings.HTML_KEEP_WHITESPACES_INSIDE, myKeepWhiteSpacesTagNames.getText().trim())) {
      return true;
    }

    if (myShouldKeepBlankLines.isSelected() != settings.HTML_KEEP_LINE_BREAKS) {
      return true;
    }

    if (myShouldKeepLineBreaksInText.isSelected() != settings.HTML_KEEP_LINE_BREAKS_IN_TEXT) {
      return true;
    }

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected String getPreviewText() {
    return readFromFile("preview.html.template");

  }

  @NotNull
  protected FileType getFileType() {
    return StdFileTypes.HTML;
  }
}
