/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  public JComponent createComponent() {
    tabbedPane = new JBTabbedPane();
    for (Configurable configurable : configurables) {
      JComponent component = configurable.createComponent();
      tabbedPane.add(configurable.getDisplayName(), component);
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
    tabbedPane = null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return selectedTabIndex < configurables.size() ? configurables.get(selectedTabIndex).getHelpTopic() : null;
  }

  @Nls
  public String getDisplayName() {
    return null;
  }
}
