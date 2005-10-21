package com.intellij.ui;

import com.intellij.openapi.options.BaseConfigurableWithChangeSupport;

public class FieldPanelWithChangeSupport {
  public static AbstractFieldPanel createPanel(AbstractFieldPanel panel, final BaseConfigurableWithChangeSupport configurable) {
    panel.setChangeListener(new Runnable() {
      public void run() {
        configurable.fireStateChanged();
      }
    });
    return panel;
  }

}
