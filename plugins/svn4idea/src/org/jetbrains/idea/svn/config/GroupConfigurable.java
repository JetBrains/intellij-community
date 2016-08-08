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
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.util.List;

public class GroupConfigurable extends NamedConfigurable<ProxyGroup> {
  private final ProxyGroup myProxyGroup;
  private final ConfigureProxiesOptionsPanel myPanel;
  private boolean myIsInitialized;

  public GroupConfigurable(final ProxyGroup proxyGroup, final Runnable treeUpdater, final ConfigureProxiesOptionsPanel panel) {
    super(! proxyGroup.isDefault(), treeUpdater);
    myProxyGroup = proxyGroup;
    myPanel = panel;
  }

  public List<String> getRepositories() {
    return myPanel.getRepositories();
  }

  public void setDisplayName(final String name) {
    myProxyGroup.setName(name);
  }

  public ProxyGroup getEditableObject() {
    return myProxyGroup;
  }

  public String getBannerSlogan() {
    return myProxyGroup.getName();
  }

  public JComponent createOptionsPanel() {
    return myPanel.getMainPanel();
  }

  public String getDisplayName() {
    return myProxyGroup.getName();
  }

  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    // not used
    return false;
  }

  public void applyImpl() {
    if (myIsInitialized) {
      myProxyGroup.setPatterns(myPanel.getPatterns());
      myPanel.copyStringProperties(myProxyGroup.getProperties());
    }
  }

  public void apply() throws ConfigurationException {
    if (myIsInitialized) {
      applyImpl();
    }
    final Ref<String> errorMessageRef = new Ref<>();
    if (! validate(errorMessageRef)) {
      throw new ConfigurationException(errorMessageRef.get());
    }
  }

  public boolean validate(final Ref<String> errorMessageRef) {
    if (! checkNumericFieldValue(myProxyGroup.getPort())) {
      errorMessageRef.set(SvnBundle.message("dialog.edit.http.proxies.settings.port.must.be.number.error", myProxyGroup.getName()));
      return false;
    }

    if (! checkNumericFieldValue(myProxyGroup.getTimeout())) {
      errorMessageRef.set(SvnBundle.message("dialog.edit.http.proxies.settings.timeout.must.be.number.error", myProxyGroup.getName()));
      return false;
    }

    return true;
  }

  public void setIsValid(final boolean valid) {
    myPanel.setIsValid(valid);
  }

  private boolean checkNumericFieldValue(final String value) {
    if (value == null) {
      return true;
    }
    try {
      final String portString = value.trim();
      if (portString.length() > 0) {
        Integer.valueOf(portString);
      }
    } catch (NumberFormatException e) {
      return false;
    }
    return true;
  }

  public void reset() {
    try {
      myPanel.setStringProperties(myProxyGroup.getProperties());
      myPanel.setPatterns(myProxyGroup.getPatterns());
      myPanel.setIsDefaultGroup(myProxyGroup.isDefault());
      myIsInitialized = true;
    } catch (NumberFormatException e) {
      // never
    }
  }

  public void disposeUIResources() {

  }
}
