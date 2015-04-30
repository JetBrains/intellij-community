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
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleSettings.DictAlignment;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author yole
 */
public class PyCodeStylePanel extends CodeStyleAbstractPanel {

  private JPanel myPanel;
  private JBCheckBox myAddTrailingLineFeedCheckbox;
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
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(new LightVirtualFile("a.py"), scheme, null);
    //return HighlighterFactory.createHighlighter(new PyHighlighter(LanguageLevel.PYTHON26), scheme);
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
      if (getCustomSettings(settings).DICT_ALIGNMENT == alignment.ordinal()) {
        myDictAlignmentCombo.setSelectedItem(alignment);
        break;
      }
    }
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    getCustomSettings(settings).DICT_ALIGNMENT = getSelectedDictAlignment().ordinal();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return getCustomSettings(settings).DICT_ALIGNMENT != getSelectedDictAlignment().ordinal();
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @NotNull
  private static PyCodeStyleSettings getCustomSettings(@NotNull CodeStyleSettings settings) {
    return settings.getCustomSettings(PyCodeStyleSettings.class);
  }

  @NotNull
  private DictAlignment getSelectedDictAlignment() {
    return (DictAlignment)myDictAlignmentCombo.getSelectedItem();
  }

  public static final String PREVIEW = "{\n" +
                                       "    \"green\": 42,\n" +
                                       "    \"eggs and ham\": -0.0e0\n" +
                                       "}";
}
