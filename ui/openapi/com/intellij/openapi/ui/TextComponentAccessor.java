/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.ui.TextFieldWithHistory;

import javax.swing.*;
import java.awt.*;

/**
 * @author dyoma
 */
public interface TextComponentAccessor<T extends Component> {
  TextComponentAccessor<JTextField> TEXT_FIELD_WHOLE_TEXT = new TextComponentAccessor<JTextField>() {
    public String getText(JTextField textField) {
      return textField.getText();
    }

    public void setText(JTextField textField, String text) {
      textField.setText(text);
    }
  };

  TextComponentAccessor<JTextField> TEXT_FIELD_SELECTED_TEXT = new TextComponentAccessor<JTextField>() {
    public String getText(JTextField textField) {
      String selectedText = textField.getSelectedText();
      return selectedText != null ? selectedText : textField.getText();
    }

    public void setText(JTextField textField, String text) {
      if (textField.getSelectedText() != null) textField.replaceSelection(text);
      else textField.setText(text);
    }
  };

  TextComponentAccessor<JComboBox> STRING_COMBOBOX_WHOLE_TEXT = new TextComponentAccessor<JComboBox>() {
    public String getText(JComboBox comboBox) {
      Object item = comboBox.getEditor().getItem();
      return item.toString();
    }

    public void setText(JComboBox comboBox, String text) {
      comboBox.getEditor().setItem(text);
    }
  };

  TextComponentAccessor<TextFieldWithHistory> TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT = new TextComponentAccessor<TextFieldWithHistory>() {
    public String getText(TextFieldWithHistory textField) {
      return textField.getText();
    }

    public void setText(TextFieldWithHistory textField, String text) {
      textField.setText(text);
    }
  };

  String getText(T component);

  void setText(T component, String text);
}
