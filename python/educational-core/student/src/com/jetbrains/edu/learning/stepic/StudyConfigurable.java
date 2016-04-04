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
package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StudyConfigurable implements SearchableConfigurable {
  public static final String ID = "com.jetbrains.edu.learning.stepic.EduConfigurable";
  private StudySettingsPanel mySettingsPane;

  public StudyConfigurable() {
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Educational";
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new StudySettingsPanel();
    }
    return mySettingsPane.getPanel();
  }

  @Override
  public boolean isModified() {
    return mySettingsPane != null && mySettingsPane.isModified();
  }

  public void apply() throws ConfigurationException {
    if (mySettingsPane != null) {
      mySettingsPane.apply();
    }
  }

  public void reset() {
    if (mySettingsPane != null) {
      mySettingsPane.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    mySettingsPane = null;
  }
}

