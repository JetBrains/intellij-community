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
package com.jetbrains.python.configuration;

import com.intellij.ide.DataManager;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author traff
 */
public class PyConfigureInterpretersLinkPanel extends JPanel {
  private final JBLabel myConfigureLabel;

  public PyConfigureInterpretersLinkPanel(final JPanel parentPanel) {
    super(new BorderLayout());
    myConfigureLabel = new JBLabel("<html><a href=\"#\">Configure Interpreters");
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
