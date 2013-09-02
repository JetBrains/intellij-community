package com.jetbrains.python.configuration;

import com.intellij.ide.DataManager;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.JBLabel;

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
      public boolean onClick(MouseEvent e, int clickCount) {
        if (clickCount == 1) {
          final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(DataManager.getInstance().getDataContext(parentPanel));
          if (optionsEditor != null) {
            PythonSdkConfigurable configurable = optionsEditor.findConfigurable(PythonSdkConfigurable.class);
            if (configurable != null) {
              optionsEditor.clearSearchAndSelect(configurable);
            }
            return true;
          }
        }
        return false;
      }
    }.installOn(myConfigureLabel);

    add(myConfigureLabel, BorderLayout.CENTER);
  }
}
