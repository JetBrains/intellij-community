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
import com.intellij.ide.browsers.BrowsersConfiguration;
import com.intellij.ide.browsers.WebBrowsersPanel;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class BrowserSettingsProviderImpl extends BrowserSettingsProvider {
  private WebBrowsersPanel mySettingsPanel;
  private final BrowsersConfiguration myConfiguration;

  public BrowserSettingsProviderImpl(@NotNull final BrowsersConfiguration configuration) {
    myConfiguration = configuration;
  }

  public JComponent createComponent() {
    if (mySettingsPanel == null) {
      mySettingsPanel = new WebBrowsersPanel(myConfiguration);
    }

    return mySettingsPanel;
  }

  public boolean isModified() {
    return mySettingsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    mySettingsPanel.apply();
  }

  public void reset() {
    mySettingsPanel.reset();
  }

  public void disposeUIResources() {
    mySettingsPanel.dispose();
    mySettingsPanel = null;
  }
}
