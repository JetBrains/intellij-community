package com.intellij.application.options;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class CodeStyleSettingsUtilImpl extends CodeStyleSettingsUtil {
  /**
   * Shows code style settings sutable for the project passed. I.e. it shows project code style page if one
   * is configured to use own code style scheme or global one in other case.
   * @param project
   * @return Returns true if settings were modified during editing session.
   */
  public boolean showCodeStyleSettings(Project project, final Class pageToSelect) {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    boolean usePerProject = settingsManager.USE_PER_PROJECT_SETTINGS;
    CodeStyleSettings savedSettings = (CodeStyleSettings)settingsManager.getCurrentSettings().clone();
    if (usePerProject) {
      final ProjectCodeStyleConfigurable configurable = ProjectCodeStyleConfigurable.getInstance(project);
      Runnable selectPage = new Runnable() {
        public void run() {
          if (pageToSelect != null) {
            configurable.selectPage(pageToSelect);
          }
        }
      };
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, selectPage);
    }
    else {
      final CodeStyleSchemesConfigurable configurable = CodeStyleSchemesConfigurable.getInstance();
      Runnable selectPage = new Runnable() {
        public void run() {
          if (pageToSelect != null) {
            configurable.selectPage(pageToSelect);
          }
        }
      };
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, selectPage);
    }

    return !savedSettings.equals(settingsManager.getCurrentSettings());
  }
}