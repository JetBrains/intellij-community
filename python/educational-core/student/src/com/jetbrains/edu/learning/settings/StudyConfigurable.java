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
package com.jetbrains.edu.learning.settings;

import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class StudyConfigurable extends CompositeConfigurable<StudyOptionsProvider> {
  public static final String ID = "com.jetbrains.edu.learning.stepic.EduConfigurable";
  private final JPanel myMainPanel;

  public StudyConfigurable() {
    myMainPanel = new JPanel(new VerticalFlowLayout());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Education";
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myMainPanel.removeAll();
    for (int i = 0; i < getConfigurables().size(); i++) {
      StudyOptionsProvider provider = getConfigurables().get(i);
      JComponent component = provider.createComponent();
      if (component != null) {
        myMainPanel.add(component);
      }
    }
    return myMainPanel;
  }

  @Override
  protected List<StudyOptionsProvider> createConfigurables() {
    return ConfigurableWrapper.createConfigurables(StudyOptionsProviderEP.EP_NAME);
  }
}

