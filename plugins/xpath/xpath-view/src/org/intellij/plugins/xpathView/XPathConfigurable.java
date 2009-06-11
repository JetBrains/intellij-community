package org.intellij.plugins.xpathView;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;
import org.intellij.plugins.xpathView.ui.ConfigUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XPathConfigurable implements Configurable {
  private ConfigUI configUI;

  public String getDisplayName() {
    return "XPath Viewer";
  }

  public Icon getIcon() {
    return IconLoader.findIcon("/icons/xml_big.png");
  }

  @Nullable
  public String getHelpTopic() {
    return "xpath.settings";
  }

  public JComponent createComponent() {
    configUI = new ConfigUI(XPathAppComponent.getInstance().getConfig());

    return configUI;
  }

  public boolean isModified() {
    if (!configUI.getConfig().equals(XPathAppComponent.getInstance().getConfig())) {
      return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    XPathAppComponent.getInstance().setConfig(configUI.getConfig());
  }

  public void reset() {
    configUI.setConfig(XPathAppComponent.getInstance().getConfig());
  }

  public void disposeUIResources() {
    configUI = null;
  }

}
