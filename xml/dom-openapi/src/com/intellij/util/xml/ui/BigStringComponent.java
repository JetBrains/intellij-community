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
package com.intellij.util.xml.ui;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.PlatformIcons;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author peter
 */
public class BigStringComponent extends TextFieldWithBrowseButton {
  private final String myWindowTitle;

  public BigStringComponent(String windowTitle) {
    this(true, windowTitle);
  }

  public BigStringComponent(boolean hasBorder, String windowTitle) {
    super();
    myWindowTitle = windowTitle;
    setButtonIcon(PlatformIcons.OPEN_EDIT_DIALOG_ICON);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(getTextField(), myWindowTitle, "DescriptionDialogEditor");
      }
    });
    if (!hasBorder) {
      getTextField().setBorder(null);
    }
  }
}
