package de.plushnikov.intellij.plugin.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class ProjectSettings {
  private static final String LOMBOK_PLUGIN_ENABLED_IN_PROJECT_PROPERTY = "LombokPluginEnabledInProject";
  private static final Key<Boolean> LOMBOK_ENABLED_KEY = Key.create(LOMBOK_PLUGIN_ENABLED_IN_PROJECT_PROPERTY);

  public static boolean isEnabledInProject(@NotNull final Project project) {
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    return properties.getBoolean(LOMBOK_PLUGIN_ENABLED_IN_PROJECT_PROPERTY, DefaultSettings.PLUGIN_ENABLED_IN_PROJECT);
  }

  public static void setEnabledInProject(@NotNull final Project project, boolean value) {
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    properties.setValue(LOMBOK_PLUGIN_ENABLED_IN_PROJECT_PROPERTY, String.valueOf(value));

    project.putUserData(LOMBOK_ENABLED_KEY, value);
  }

  public static boolean loadAndGetEnabledInProject(@NotNull final Project project) {
    final boolean result;
    Boolean enabledInProject = project.getUserData(LOMBOK_ENABLED_KEY);
    if (null == enabledInProject) {
      result = isEnabledInProject(project);
      project.putUserData(LOMBOK_ENABLED_KEY, result);
    } else {
      result = enabledInProject;
    }
    return result;
  }
}
