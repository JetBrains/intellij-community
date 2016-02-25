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
package com.jetbrains.edu.learning.ui;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

public class StudySwingToolWindow extends StudyToolWindow {
  private JTextPane myTaskTextPane;

  public StudySwingToolWindow() {
    super();
  }

  @Override
  public JComponent createTaskInfoPanel(String taskText) {
    myTaskTextPane = new JTextPane();
    myTaskTextPane.setContentType(new HTMLEditorKit().getContentType());
    final EditorColorsScheme editorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    int fontSize = editorColorsScheme.getEditorFontSize();
    final String fontName = editorColorsScheme.getEditorFontName();
    final Font font = new Font(fontName, Font.PLAIN, fontSize);
    String bodyRule = "body { font-family: " + font.getFamily() + "; " +
                      "font-size: " + font.getSize() + "pt; }" +
                      "pre {font-family: Courier; display: inline; ine-height: 50px; padding-top: 5px; padding-bottom: 5px; padding-left: 5px; background-color:"
                      + ColorUtil.toHex(ColorUtil.dimmer(UIUtil.getPanelBackground())) + ";}" +
                      "code {font-family: Courier; display: flex; float: left; background-color:"
                      + ColorUtil.toHex(ColorUtil.dimmer(UIUtil.getPanelBackground())) + ";}";
    ((HTMLDocument)myTaskTextPane.getDocument()).getStyleSheet().addRule(bodyRule);
    myTaskTextPane.setEditable(false);
    if (!UIUtil.isUnderDarcula()) {
      myTaskTextPane.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    }
    myTaskTextPane.setBorder(new EmptyBorder(15, 20, 0, 100));
    myTaskTextPane.setText(taskText);
    myTaskTextPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    return myTaskTextPane;
  }

  public void setTaskText(String text) {
    myTaskTextPane.setText(text);
  }
}

