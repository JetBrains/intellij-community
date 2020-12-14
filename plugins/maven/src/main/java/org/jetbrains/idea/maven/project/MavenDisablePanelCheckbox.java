// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class MavenDisablePanelCheckbox extends JCheckBox {

  private final JComponent myPanel;
  private Set<JComponent> myDisabledComponents;

  public MavenDisablePanelCheckbox(@NlsContexts.Label String text, @NotNull JComponent panel) {
    super(text);
    myPanel = panel;

    addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (MavenDisablePanelCheckbox.this.isEnabled() && MavenDisablePanelCheckbox.this.isSelected()) {
          if (myDisabledComponents == null) {
            myDisabledComponents = new HashSet<>();
            disable(myPanel);
          }
        }
        else {
          if (myDisabledComponents != null) {
            enable(myPanel);
            myDisabledComponents = null;
          }
        }

      }
    });
  }

  private void disable(JComponent c) {
    if (c.isEnabled()) {
      myDisabledComponents.add(c);
      c.setEnabled(false);
    }

    for (Component component : c.getComponents()) {
      if (component instanceof JComponent) {
        disable((JComponent)component);
      }
    }
  }

  private void enable(JComponent c) {
    if (myDisabledComponents.contains(c)) {
      c.setEnabled(true);
    }

    for (Component component : c.getComponents()) {
      if (component instanceof JComponent) {
        enable((JComponent)component);
      }
    }
  }

  public static Pair<JPanel, JCheckBox> createPanel(JComponent component, @NlsContexts.Label String title) {
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        Color c = enabled ? JBColor.GRAY : JBColor.LIGHT_GRAY;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, c), BorderFactory.createEmptyBorder(10, 0, 0, 0)));
      }
    };
    panel.setEnabled(true);
    panel.add(component);

    JCheckBox checkbox = new MavenDisablePanelCheckbox(title, panel);

    JPanel res = new JPanel(new BorderLayout(0, 10));
    res.add(checkbox, BorderLayout.NORTH);
    res.add(panel, BorderLayout.CENTER);

    return Pair.create(res, checkbox);

  }

}
