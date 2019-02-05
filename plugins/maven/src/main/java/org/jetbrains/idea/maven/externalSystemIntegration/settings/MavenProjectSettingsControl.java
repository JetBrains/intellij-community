// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;

import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.settings.MavenProjectSettings;

public class MavenProjectSettingsControl implements ExternalSystemSettingsControl<MavenProjectSettings> {
  public MavenProjectSettingsControl(MavenProjectSettings initialSettings) {}

  @Override
  public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {

  }

  @Override
  public void reset() {

  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply(@NotNull MavenProjectSettings settings) {

  }

  @Override
  public boolean validate(@NotNull MavenProjectSettings settings) throws ConfigurationException {
    return false;
  }

  @Override
  public void disposeUIResources() {

  }

  @Override
  public void showUi(boolean show) {

  }
}
