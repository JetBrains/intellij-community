package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:54:57 PM
 */
public class ContentEntriesEditor extends CommonContentEntriesEditor {
  private LanguageLevelConfigurable myLanguageLevelConfigurable;

  public ContentEntriesEditor(Project project, String moduleName, ModifiableRootModel model, ModulesProvider modulesProvider) {
    super(project, moduleName, model, modulesProvider);
  }

  public void disposeUIResources() {
    myLanguageLevelConfigurable.disposeUIResources();
    super.disposeUIResources();
  }

  public boolean isModified() {
    if (super.isModified()) {
      return true;
    }
    if (myLanguageLevelConfigurable != null && myLanguageLevelConfigurable.isModified()) return true;
    return false;
  }

  protected void addAdditionalSettingsToPanel(final JPanel mainPanel) {
    myLanguageLevelConfigurable = new LanguageLevelConfigurable(myModel);
    mainPanel.add(myLanguageLevelConfigurable.createComponent(), BorderLayout.NORTH);
    myLanguageLevelConfigurable.reset();
  }

  public void apply() throws ConfigurationException {
    myLanguageLevelConfigurable.apply();
    super.apply();
  }
}
