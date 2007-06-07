/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.update.ComponentDisposable;

import javax.swing.*;
import java.awt.event.ActionListener;

public class TextFieldWithBrowseButton extends ComponentWithBrowseButton<JTextField> {
  public TextFieldWithBrowseButton(){
    this((ActionListener)null);
  }

  public TextFieldWithBrowseButton(JTextField field){
    this(field, null);
  }

  public TextFieldWithBrowseButton(JTextField field, ActionListener browseActionListener) {
    super(field, browseActionListener);
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener) {
    this(new JTextField(), browseActionListener);
  }

  public void addBrowseFolderListener(String title, String description, Project project, FileChooserDescriptor fileChooserDescriptor) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    FileChooserFactory.getInstance().installFileCompletion(getChildComponent(), fileChooserDescriptor, true, new ComponentDisposable(getChildComponent()));
  }

  public JTextField getTextField() {
    return getChildComponent();
  }

  /**
   * @return trimmed text
   */
  public String getText(){
    return getTextField().getText();
  }

  public void setText(final String text){
    getTextField().setText(text);
  }

  public boolean isEditable() {
    return getTextField().isEditable();
  }

  public void setEditable(boolean b) {
    getTextField().setEditable(b);
  }
}
