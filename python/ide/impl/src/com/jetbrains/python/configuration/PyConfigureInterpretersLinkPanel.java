// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.ide.DataManager;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PyConfigureInterpretersLinkPanel extends JPanel {
  private final JBLabel myConfigureLabel;

  public PyConfigureInterpretersLinkPanel(final JPanel parentPanel) {
    super(new BorderLayout());
    myConfigureLabel = new JBLabel(PyBundle.message("configuring.interpreters.link"));
    myConfigureLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (clickCount == 1) {
          Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(parentPanel));
          if (settings != null) {
            settings.select(settings.find(PyActiveSdkModuleConfigurable.class.getName()));
            return true;
          }
        }
        return false;
      }
    }.installOn(myConfigureLabel);

    add(myConfigureLabel, BorderLayout.CENTER);
  }
}
