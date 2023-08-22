// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BigStringComponent extends TextFieldWithBrowseButton {
  private final @NlsContexts.DialogTitle String myWindowTitle;

  public BigStringComponent(@NlsContexts.DialogTitle String windowTitle) {
    this(true, windowTitle);
  }

  public BigStringComponent(boolean hasBorder, @NlsContexts.DialogTitle String windowTitle) {
    super();
    myWindowTitle = windowTitle;
    setButtonIcon(AllIcons.Actions.ShowViewer);
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
