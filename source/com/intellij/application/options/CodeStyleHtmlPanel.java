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
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.Icons;
import com.intellij.util.ValueHolder;

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
  private TextFieldWithBrowseButton myTextElementsTagNames;
  private JTextField myDoNotAlignChildrenMinSize;
  private JCheckBox myShouldKeepBlankLines;
  private JCheckBox mySpaceInEmptyTag;
  private JCheckBox myWrapText;
  private TextFieldWithBrowseButton myAlwaysWrapTags;

  public CodeStyleHtmlPanel(CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);

    fillWrappingCombo(myWrapAttributes);

    customizeField("Insert New Line Before Tags", myInsertNewLineTagNames, new ValueHolder<String, CodeStyleSettings>() {
      public String getValue(final CodeStyleSettings dataHolder) {
        return dataHolder.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE;
      }

      public void setValue(final String value, final CodeStyleSettings dataHolder) {
        dataHolder.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = value;
      }
    });

    customizeField("Always Wrap Tags", myAlwaysWrapTags, new ValueHolder<String, CodeStyleSettings>() {
      public String getValue(final CodeStyleSettings dataHolder) {
        return dataHolder.HTML_PLACE_ON_NEW_LINE;
      }

      public void setValue(final String value, final CodeStyleSettings dataHolder) {
        dataHolder.HTML_PLACE_ON_NEW_LINE = value;
      }
    });

    customizeField("Remove Line Breaks Before Tags", myRemoveNewLineTagNames, new ValueHolder<String, CodeStyleSettings>() {
      public String getValue(final CodeStyleSettings dataHolder) {
        return dataHolder.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE;
      }

      public void setValue(final String value,final CodeStyleSettings dataHolder) {
        dataHolder.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = value;
      }
    });

    customizeField("Do not Indent Children Of", myDoNotAlignChildrenTagNames, new ValueHolder<String, CodeStyleSettings>() {
      public String getValue(final CodeStyleSettings dataHolder) {
        return dataHolder.HTML_DO_NOT_INDENT_CHILDREN_OF;
      }

      public void setValue(final String value,final CodeStyleSettings dataHolder) {
        dataHolder.HTML_DO_NOT_INDENT_CHILDREN_OF = value;
      }
    });

    customizeField("Text Elements", myTextElementsTagNames, new ValueHolder<String, CodeStyleSettings>() {
      public String getValue(final CodeStyleSettings dataHolder) {
        return dataHolder.HTML_TEXT_ELEMENTS;
      }

      public void setValue(final String value,final CodeStyleSettings dataHolder) {
        dataHolder.HTML_TEXT_ELEMENTS = value;
      }
    });

    customizeField("Keep Whitespaces Inside", myKeepWhiteSpacesTagNames, new ValueHolder<String, CodeStyleSettings>() {
      public String getValue(final CodeStyleSettings dataHolder) {
        return dataHolder.HTML_KEEP_WHITESPACES_INSIDE;
      }

      public void setValue(final String value,final CodeStyleSettings dataHolder) {
        dataHolder.HTML_KEEP_WHITESPACES_INSIDE = value;
      }
    });


    addPanelToWatch(myPanel);
  }

  protected LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createXMLHighlighter(scheme);
  }

  private void customizeField(final String title, final TextFieldWithBrowseButton uiField, final ValueHolder<String, CodeStyleSettings> valueHolder) {
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
    settings.HTML_TEXT_WRAP = myWrapText.isSelected()
                              ? CodeStyleSettings.WRAP_AS_NEEDED : CodeStyleSettings.DO_NOT_WRAP;
    settings.HTML_SPACE_INSIDE_EMPTY_TAG = mySpaceInEmptyTag.isSelected();
    settings.HTML_ALIGN_ATTRIBUTES = myAlignAttributes.isSelected();
    settings.HTML_ALIGN_TEXT = myAlignText.isSelected();
    settings.HTML_KEEP_WHITESPACES = myKeepWhiteSpaces.isSelected();
    settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE = mySpacesAroundEquality.isSelected();
    settings.HTML_SPACE_AROUND_TAG_NAME = mySpacesAroundTagName.isSelected();

    settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = myInsertNewLineTagNames.getText();
    settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = myRemoveNewLineTagNames.getText();
    settings.HTML_DO_NOT_INDENT_CHILDREN_OF = myDoNotAlignChildrenTagNames.getText();
    settings.HTML_PLACE_ON_NEW_LINE = myAlwaysWrapTags.getText();
    settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = getIntValue(myDoNotAlignChildrenMinSize);
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

    myAlwaysWrapTags.setText(settings.HTML_PLACE_ON_NEW_LINE);
    myInsertNewLineTagNames.setText(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
    myRemoveNewLineTagNames.setText(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
    myDoNotAlignChildrenTagNames.setText(settings.HTML_DO_NOT_INDENT_CHILDREN_OF);
    myDoNotAlignChildrenMinSize.setText(String.valueOf(settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES));
    myTextElementsTagNames.setText(settings.HTML_TEXT_ELEMENTS);
    myKeepWhiteSpacesTagNames.setText(settings.HTML_KEEP_WHITESPACES_INSIDE);
  }

  public boolean isModified(CodeStyleSettings settings) {
    if (settings.HTML_KEEP_BLANK_LINES != getIntValue(myKeepBlankLines)) {
      return true;
    }
    if (settings.HTML_ATTRIBUTE_WRAP != ourWrappings[myWrapAttributes.getSelectedIndex()]) {
      return true;
    }

    if ((settings.HTML_TEXT_WRAP == CodeStyleSettings.WRAP_AS_NEEDED) !=
        myWrapText.isSelected()) {
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

    if (settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE != mySpacesAroundEquality.isSelected()){
      return true;
    }

    if (settings.HTML_SPACE_AROUND_TAG_NAME != mySpacesAroundTagName.isSelected()){
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE, myInsertNewLineTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_PLACE_ON_NEW_LINE, myAlwaysWrapTags.getText().trim())) {
      return true;
    }

    if (!Comparing.equal(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE, myRemoveNewLineTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_DO_NOT_INDENT_CHILDREN_OF, myDoNotAlignChildrenTagNames.getText().trim())){
      return true;
    }

    if (settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES != getIntValue(myDoNotAlignChildrenMinSize)){
      return true;
    }

    if (!Comparing.equal(settings.HTML_TEXT_ELEMENTS, myTextElementsTagNames.getText().trim())){
      return true;
    }

    if (!Comparing.equal(settings.HTML_KEEP_WHITESPACES_INSIDE, myKeepWhiteSpacesTagNames.getText().trim())){
      return true;
    }

    if (myShouldKeepBlankLines.isSelected() != settings.HTML_KEEP_LINE_BREAKS) {
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

  protected FileType getFileType() {
    return StdFileTypes.HTML;
  }
}
