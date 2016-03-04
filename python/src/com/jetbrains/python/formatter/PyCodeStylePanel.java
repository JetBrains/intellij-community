/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleSettings.DictAlignment;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author yole
 */
public class PyCodeStylePanel extends CodeStyleAbstractPanel {

  private JPanel myPanel;
  private JBCheckBox myAddTrailingBlankLineCheckbox;
  private JBCheckBox myUseContinuationIndentForArguments;
  private ComboBox myDictAlignmentCombo;
  private JPanel myPreviewPanel;

  protected PyCodeStylePanel(CodeStyleSettings settings) {
    super(PythonLanguage.getInstance(), null, settings);
    addPanelToWatch(myPanel);
    installPreviewPanel(myPreviewPanel);

    for (DictAlignment alignment : DictAlignment.values()) {
      //noinspection unchecked
      myDictAlignmentCombo.addItem(alignment);
    }

    myDictAlignmentCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          somethingChanged();
        }
      }
    });

    myAddTrailingBlankLineCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        somethingChanged();
      }
    });
    
    myUseContinuationIndentForArguments.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        somethingChanged();
      }
    });
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return HighlighterFactory.createHighlighter(new PyHighlighter(LanguageLevel.PYTHON26), scheme);
  }

  @Override
  protected int getRightMargin() {
    return 80;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return PREVIEW;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    for (DictAlignment alignment : DictAlignment.values()) {
      if (getCustomSettings(settings).DICT_ALIGNMENT == alignment.asInt()) {
        myDictAlignmentCombo.setSelectedItem(alignment);
        break;
      }
    }
    myAddTrailingBlankLineCheckbox.setSelected(getCustomSettings(settings).BLANK_LINE_AT_FILE_END);
    myUseContinuationIndentForArguments.setSelected(getCustomSettings(settings).USE_CONTINUATION_INDENT_FOR_ARGUMENTS);
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final PyCodeStyleSettings customSettings = getCustomSettings(settings);
    customSettings.DICT_ALIGNMENT = getDictAlignmentAsInt();
    customSettings.BLANK_LINE_AT_FILE_END = ensureTrailingBlankLine();
    customSettings.USE_CONTINUATION_INDENT_FOR_ARGUMENTS = useContinuationIndentForArguments();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final PyCodeStyleSettings customSettings = getCustomSettings(settings);
    return customSettings.DICT_ALIGNMENT != getDictAlignmentAsInt() ||
           customSettings.BLANK_LINE_AT_FILE_END != ensureTrailingBlankLine();
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @NotNull
  private static PyCodeStyleSettings getCustomSettings(@NotNull CodeStyleSettings settings) {
    return settings.getCustomSettings(PyCodeStyleSettings.class);
  }

  private int getDictAlignmentAsInt() {
    return ((DictAlignment)myDictAlignmentCombo.getSelectedItem()).asInt();
  }

  private boolean ensureTrailingBlankLine() {
    return myAddTrailingBlankLineCheckbox.isSelected();
  }

  private boolean useContinuationIndentForArguments() {
    return myUseContinuationIndentForArguments.isSelected();
  }

  public static final String PREVIEW = "x = max(\n" +
    "    1,\n" +
    "    2,\n" +
    "    3)\n" +
    "\n" +
    "{\n" +
    "    \"green\": 42,\n" +
    "    \"eggs and ham\": -0.0e0\n" +
    "}";
}
