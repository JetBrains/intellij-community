/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BrowserSettings implements SearchableConfigurable, Configurable.NoScroll {
  private BrowserSettingsPanel myPanel;

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return IdeBundle.message("browsers.settings");
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "reference.settings.ide.settings.web.browsers";
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new BrowserSettingsPanel();
    }
    return myPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  @Override
  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  public void selectBrowser(@NotNull WebBrowser browser) {
    createComponent();
    myPanel.selectBrowser(browser);
  }
}
