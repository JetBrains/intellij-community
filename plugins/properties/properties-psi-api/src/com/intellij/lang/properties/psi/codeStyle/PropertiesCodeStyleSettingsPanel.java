/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCodeStyleSettingsPanel extends CodeStyleAbstractPanel {
  private final static String WHITESPACE_ELEMENT = "Whitespace symbol";

  private ComboBox myDelimiterCombo;
  private JPanel myPanel;

  public PropertiesCodeStyleSettingsPanel(CodeStyleSettings settings) {
    super(settings);
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement(':');
    model.addElement('=');
    model.addElement(WHITESPACE_ELEMENT);
    myDelimiterCombo.setModel(model);
    selectChar(settings.getCustomSettings(PropertiesCodeStyleSettings.class));
  }

  private void selectChar(PropertiesCodeStyleSettings settings) {
    myDelimiterCombo.setSelectedItem(settings.KEY_VALUE_DELIMITER == ' ' ? WHITESPACE_ELEMENT : settings.KEY_VALUE_DELIMITER);
  }

  private char getSelectedChar() {
    final Object item = myDelimiterCombo.getModel().getSelectedItem();
    if (item instanceof Character) {
      return (Character)item;
    }
    assert item == WHITESPACE_ELEMENT;
    return ' ';
  }

  private void createUIComponents() {
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return StdFileTypes.PROPERTIES;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    final PropertiesCodeStyleSettings propertiesCodeStyleSettings = settings.getCustomSettings(PropertiesCodeStyleSettings.class);
    propertiesCodeStyleSettings.KEY_VALUE_DELIMITER = getSelectedChar();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final PropertiesCodeStyleSettings propertiesCodeStyleSettings = settings.getCustomSettings(PropertiesCodeStyleSettings.class);
    return propertiesCodeStyleSettings.KEY_VALUE_DELIMITER != getSelectedChar();
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    selectChar(settings.getCustomSettings(PropertiesCodeStyleSettings.class));
  }
}
