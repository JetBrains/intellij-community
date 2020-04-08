/*
 * Copyright 2005-2009 Sascha Weinreuter
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
package org.intellij.plugins.xpathView;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.intellij.plugins.xpathView.ui.ConfigUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XPathConfigurable implements SearchableConfigurable {
    private ConfigUI configUI;

    @Override
  public String getDisplayName() {
    return XPathBundle.message("configurable.XPathConfigurable.display.name");
  }

  @Override
  @Nullable
    public String getHelpTopic() {
        return "xpath.settings";
    }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
        configUI = new ConfigUI(XPathAppComponent.getInstance().getConfig());

        return configUI;
    }

    @Override
    public synchronized boolean isModified() {
        return configUI != null && !configUI.getConfig().equals(XPathAppComponent.getInstance().getConfig());
    }

    @Override
    public synchronized void apply() throws ConfigurationException {
        if (configUI != null) {
            XPathAppComponent.getInstance().setConfig(configUI.getConfig());
        }
    }

    @Override
    public synchronized void reset() {
        if (configUI != null) {
            configUI.setConfig(XPathAppComponent.getInstance().getConfig());
        }
    }

    @Override
    public synchronized void disposeUIResources() {
        configUI = null;
    }
}
