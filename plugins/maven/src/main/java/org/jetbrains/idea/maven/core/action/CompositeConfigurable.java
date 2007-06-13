package org.jetbrains.idea.maven.core.action;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class CompositeConfigurable implements Configurable {
  private final List<Configurable> configurables = new ArrayList<Configurable>();
  private JTabbedPane tabbedPane;
  private int selectedTabIndex = 0;

  void registerConfigurable(Configurable configurable) {
    configurables.add(configurable);
  }

  public JComponent createComponent() {
    tabbedPane = new JTabbedPane();
    for (Configurable configurable : configurables) {
      tabbedPane.add(configurable.getDisplayName(), configurable.createComponent());
    }
    return tabbedPane;
  }

  public boolean isModified() {
    for (Configurable configurable : configurables) {
      if (configurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : configurables) {
      configurable.apply();
    }
    selectedTabIndex = tabbedPane.getSelectedIndex();
  }

  public void reset() {
    for (Configurable configurable : configurables) {
      configurable.reset();
    }
    tabbedPane.setSelectedIndex(selectedTabIndex);
  }

  public void disposeUIResources() {
    for (Configurable configurable : configurables) {
      configurable.disposeUIResources();
    }
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return selectedTabIndex < configurables.size() ? configurables.get(selectedTabIndex).getHelpTopic() : null;
  }
}