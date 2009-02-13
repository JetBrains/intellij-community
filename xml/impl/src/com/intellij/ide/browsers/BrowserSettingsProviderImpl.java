package com.intellij.ide.browsers;

import com.intellij.ide.BrowserSettingsProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class BrowserSettingsProviderImpl extends BrowserSettingsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.BrowserSettingsProviderImpl");
  
  private WebBrowsersPanel mySettingsPanel;
  private BrowsersConfiguration myConfiguration;

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
    LOG.assertTrue(mySettingsPanel != null);
    return mySettingsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    LOG.assertTrue(mySettingsPanel != null);
    mySettingsPanel.apply();
  }

  public void reset() {
    LOG.assertTrue(mySettingsPanel != null);
    mySettingsPanel.reset();
  }

  public void disposeUIResources() {
    mySettingsPanel.dispose();
    mySettingsPanel = null;
  }

}
