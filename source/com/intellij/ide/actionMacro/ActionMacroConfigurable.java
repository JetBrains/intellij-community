package com.intellij.ide.actionMacro;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 21, 2003
 * Time: 10:38:42 PM
 * To change this template use Options | File Templates.
 */
public class ActionMacroConfigurable implements Configurable {
  private ActionMacroConfigurationPanel myPanel;

  public String getDisplayName() {
    return "Edit Macros";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    myPanel = new ActionMacroConfigurationPanel();
    return myPanel.getPanel();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void disposeUIResources() {
    myPanel = null;
  }
}
