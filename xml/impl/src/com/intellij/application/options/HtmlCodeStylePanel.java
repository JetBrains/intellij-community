/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.application.options.codeStyle.RightMarginForm;
import com.intellij.ide.highlighter.XmlHighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class HtmlCodeStylePanel extends CodeStyleAbstractPanel {

  private HtmlCodeStyleSettingsForm myHtmlForm;
  private RightMarginForm myRightMarginForm;
  private JBScrollPane myJBScrollPane;
  private JPanel myRightMarginPanel;
  private JPanel myHtmlPanel;
  private JPanel myPreviewPanel;
  private JPanel myPanel;

  protected HtmlCodeStylePanel(@NotNull CodeStyleSettings settings) {
    super(settings);
    installPreviewPanel(myPreviewPanel);
    //addPanelToWatch(myPanel);
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected String getPreviewText() {
    return readFromFile(this.getClass(), "preview.html.template");
  }

  @Override
  protected int getRightMargin() {
    return 60;
  }

  @Override
  @NotNull
  protected FileType getFileType() {
    return StdFileTypes.HTML;
  }

  @Override
  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return XmlHighlighterFactory.createXMLHighlighter(scheme);
  }

  private void createUIComponents() {
    myHtmlForm = new HtmlCodeStyleSettingsForm();
    myHtmlPanel = myHtmlForm.getTopPanel();
    myRightMarginForm = new RightMarginForm(StdFileTypes.HTML.getLanguage(), getSettings());
    myRightMarginPanel = myRightMarginForm.getTopPanel();
    myJBScrollPane = new JBScrollPane() {
      @Override
      public Dimension getPreferredSize() {
        Dimension prefSize = super.getPreferredSize();
        return new Dimension(prefSize.width + 15, prefSize.height);
      }
    };
  }

  @Override
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    HtmlCodeStyleSettings htmlSettings = settings.getCustomSettings(HtmlCodeStyleSettings.class);
    myHtmlForm.apply(htmlSettings);
    myRightMarginForm.apply(settings);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    HtmlCodeStyleSettings htmlSettings = settings.getCustomSettings(HtmlCodeStyleSettings.class);
    return myHtmlForm.isModified(htmlSettings) || myRightMarginForm.isModified(settings);
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    HtmlCodeStyleSettings htmlSettings = settings.getCustomSettings(HtmlCodeStyleSettings.class);
    myHtmlForm.reset(htmlSettings);
    myRightMarginForm.reset(settings);
  }
}
