package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 15, 2003
 */
public class ModulesConfigurable implements Configurable {
  private final Project myProject;
  private final String myModuleNameToSelect;
  private final String myTabNameToSelect;
  private final boolean myStartModuleWizardOnShow;
  private ModulesConfigurator myConfigurator;
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");

  public ModulesConfigurable(Project project) {
    this(project, null, null, false);
  }

  public ModulesConfigurable(Project project, String moduleNameToSelect, String tabNameToSelect, boolean startModuleWizard) {
    myProject = project;
    myModuleNameToSelect = moduleNameToSelect;
    myTabNameToSelect = tabNameToSelect;
    myStartModuleWizardOnShow = startModuleWizard;
  }

  public String getDisplayName() {
    return "Paths";
  }

  public void reset() {
    if (myConfigurator != null) {
      if (isModified()) {
        myConfigurator.reset();
      }
      final String moduleNameToSelect = getModuleNameToSelect();
      if (moduleNameToSelect == null || !myConfigurator.selectModule(moduleNameToSelect, getTabNameToSelect())) {
        myConfigurator.selectFirstModule();
      }
    }
  }

  public void apply() throws ConfigurationException {
    myConfigurator.apply();
  }

  public String getHelpTopic() {
    if (myConfigurator != null) {
      final String helpTopic = myConfigurator.getHelpTopic();
      if (helpTopic != null) {
        return helpTopic;
      }
    }
    return "project.paths";
  }

  public void disposeUIResources() {
    if (myConfigurator != null) {
      final ModuleEditorState state = ModuleEditorState.getInstance(myProject);
      state.LAST_EDITED_MODULE_NAME = myConfigurator.getSelectedModuleName();
      state.LAST_EDITED_TAB_NAME = myConfigurator.getSelectedTabName();
      myConfigurator.dispose();
      myConfigurator = null; // important: becomes invalid after destroyProcess
    }
  }

  public boolean isModified() {
    return myConfigurator.isModified();
  }

  public JComponent createComponent() {
    if (myConfigurator == null) {
      myConfigurator = new ModulesConfigurator(myProject, myStartModuleWizardOnShow);
    }
    return myConfigurator.createComponent();
  }

  public Icon getIcon() {
    return ICON;
  }

  public ModulesConfigurator getConfigurator() {
    return myConfigurator;
  }

  private String getModuleNameToSelect() {
    if (myModuleNameToSelect == null) {
      return ModuleEditorState.getInstance(myProject).LAST_EDITED_MODULE_NAME;
    }
    return myModuleNameToSelect;
  }

  private String getTabNameToSelect() {
    if (myTabNameToSelect == null) {
      return ModuleEditorState.getInstance(myProject).LAST_EDITED_TAB_NAME;
    }
    return myTabNameToSelect;
  }
}
