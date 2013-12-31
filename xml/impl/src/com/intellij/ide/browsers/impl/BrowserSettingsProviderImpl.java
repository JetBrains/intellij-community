/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.browsers.impl;

import com.intellij.ide.BrowserSettingsProvider;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowsersPanel;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BrowserSettingsProviderImpl extends BrowserSettingsProvider {
  private WebBrowsersPanel mySettingsPanel;
  private final WebBrowserManager myConfiguration;

  public BrowserSettingsProviderImpl(@NotNull WebBrowserManager configuration) {
    myConfiguration = configuration;
  }

  @Override
  public JComponent createComponent() {
    if (mySettingsPanel == null) {
      mySettingsPanel = new WebBrowsersPanel(myConfiguration);
    }

    return mySettingsPanel;
  }

  @Override
  public boolean isModified() {
    return mySettingsPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettingsPanel.apply();
  }

  @Override
  public void reset() {
    mySettingsPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    mySettingsPanel.dispose();
    mySettingsPanel = null;
  }
}
