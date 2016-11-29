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

import com.intellij.application.options.codeStyle.OptionTableWithPreviewPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCodeStyleSettingsPanel extends OptionTableWithPreviewPanel {
  public PropertiesCodeStyleSettingsPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.BLANK_LINES_SETTINGS;
  }

  @Override
  protected void initTables() {
    addOption("ALIGN_GROUP_FIELD_DECLARATIONS", "Align properties in column");
    showStandardOptions("ALIGN_GROUP_FIELD_DECLARATIONS");
    showCustomOption(PropertiesCodeStyleSettings.class, "SPACES_AROUND_KEY_VALUE_DELIMITER",
                     "Insert space around key-value delimiter", null);
    showCustomOption(PropertiesCodeStyleSettings.class,
                     "KEY_VALUE_DELIMITER_CODE",
                     "Key-value delimiter", null,
                     new String[]{"=", ":", "whitespace symbol"}, new int[]{0, 1, 2});
    showCustomOption(PropertiesCodeStyleSettings.class, "KEEP_BLANK_LINES",
                     "Keep blank lines", null);
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(new LightVirtualFile("p.properties"), scheme, null);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return StdFileTypes.PROPERTIES;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return "key1=value\n" +
           "some_key=some_value\n\n" +
           "#commentaries\n" +
           "last.key=some text here";
  }
}
