package de.plushnikov.intellij.plugin.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectSettings {
  private static final String PREFIX = "LombokPlugin";

  public static final String LOMBOK_ENABLED_IN_PROJECT = PREFIX + "EnabledInProject";
  public static final String IS_VAL_ENABLED = PREFIX + "IS_VAL_Enabled";
  public static final String IS_BUILDER_ENABLED = PREFIX + "IS_BUILDER_Enabled";
  public static final String IS_SUPER_BUILDER_ENABLED = PREFIX + "IS_SUPER_BUILDER_Enabled";
  public static final String IS_DELEGATE_ENABLED = PREFIX + "IS_DELEGATE_Enabled";
  public static final String IS_LOG_ENABLED = PREFIX + "IS_LOG_Enabled";
  public static final String IS_CONSTRUCTOR_ENABLED = PREFIX + "IS_CONSTRUCTOR_Enabled";

  public static final String IS_ANNOTATION_PROCESSING_CHECK_ENABLED = PREFIX + "IS_ANNOTATION_PROCESSING_CHECK_Enabled";
  public static final String IS_LOMBOK_VERSION_CHECK_ENABLED = PREFIX + "IS_LOMBOK_VERSION_CHECK_Enabled";
  public static final String IS_MISSING_LOMBOK_CHECK_ENABLED = PREFIX + "IS_MISSING_LOMBOK_CHECK_Enabled";

  public static boolean isLombokEnabledInProject(@NotNull final Project project) {
    return isEnabled(project, LOMBOK_ENABLED_IN_PROJECT);
  }

  public static void setLombokEnabledInProject(@NotNull final Project project, boolean value) {
    setEnabled(project, LOMBOK_ENABLED_IN_PROJECT, value);
  }

  public static boolean isEnabled(@NotNull Project project, final String propertyName) {
    return isEnabled(PropertiesComponent.getInstance(project), propertyName);
  }

  public static boolean isEnabled(@NotNull Project project, final String propertyName, boolean defaultValue) {
    return isEnabled(PropertiesComponent.getInstance(project), propertyName, defaultValue);
  }

  public static boolean isEnabled(PropertiesComponent properties, String propertyName) {
    return isEnabled(properties, propertyName, true);
  }

  public static boolean isEnabled(PropertiesComponent properties, String propertyName, boolean defaultValue) {
    return properties.getBoolean(propertyName, defaultValue);
  }

  private static void setEnabled(@NotNull Project project, final String propertyName, boolean value) {
    setEnabled(PropertiesComponent.getInstance(project), propertyName, value);
  }

  public static void setEnabled(PropertiesComponent properties, String propertyName, boolean value) {
    properties.setValue(propertyName, String.valueOf(value), "");
  }

}
