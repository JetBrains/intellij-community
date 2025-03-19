// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class CompositeConfigurable implements Configurable {
  private final List<Configurable> configurables = new ArrayList<>();
  private JTabbedPane tabbedPane;
  private int selectedTabIndex = 0;

  public CompositeConfigurable(Configurable... configurables) {
    for (Configurable configurable : configurables) {
      registerConfigurable(configurable);
    }
  }

  public void registerConfigurable(Configurable configurable) {
    configurables.add(configurable);
  }

  @Override
  public JComponent createComponent() {
    tabbedPane = new JBTabbedPane();
    for (Configurable configurable : configurables) {
      JComponent component = configurable.createComponent();
      tabbedPane.add(configurable.getDisplayName(), component);
    }
    return tabbedPane;
  }

  @Override
  public boolean isModified() {
    for (Configurable configurable : configurables) {
      if (configurable.isModified()) return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (Configurable configurable : configurables) {
      configurable.apply();
    }
    selectedTabIndex = tabbedPane.getSelectedIndex();
  }

  @Override
  public void reset() {
    for (Configurable configurable : configurables) {
      configurable.reset();
    }
    tabbedPane.setSelectedIndex(selectedTabIndex);
  }

  @Override
  public void disposeUIResources() {
    for (Configurable configurable : configurables) {
      configurable.disposeUIResources();
    }
    tabbedPane = null;
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return selectedTabIndex < configurables.size() ? configurables.get(selectedTabIndex).getHelpTopic() : null;
  }

  @Override
  public @Nls String getDisplayName() {
    return null;
  }
}
