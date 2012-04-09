/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenGeneralConfigurableWithUseProjectSettings extends MavenGeneralConfigurable {

  private JCheckBox myUseProjectSettings;

  private final Project myProject;

  protected MavenGeneralConfigurableWithUseProjectSettings(@NotNull Project project) {
    myProject = project;
  }

  public abstract void setState(@Nullable MavenGeneralSettings state);

  @Override
  public boolean isModified() {
    if (myUseProjectSettings.isSelected()) {
      return getState() != null;
    }
    else {
      return getState() == null || super.isModified();
    }
  }

  @Override
  public void apply() {
    if (myUseProjectSettings.isSelected()) {
      setState(null);
    }
    else {
      MavenGeneralSettings state = getState();
      if (state != null) {
        setData(state);
      }
      else {
        MavenGeneralSettings settings = MavenProjectsManager.getInstance(myProject).getGeneralSettings().clone();
        setData(settings);
        setState(settings);
      }
    }
  }

  @Override
  public void reset() {
    MavenGeneralSettings state = getState();
    myUseProjectSettings.setSelected(state == null);

    if (state == null) {
      MavenGeneralSettings settings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
      getData(settings);
    }
    else {
      getData(state);
    }
  }

  @Override
  public JComponent createComponent() {
    Pair<JPanel,JCheckBox> pair = MavenDisablePanelCheckbox.createPanel(super.createComponent(), "Use project settings");

    myUseProjectSettings = pair.second;
    return pair.first;
  }
}
