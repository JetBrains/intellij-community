package de.plushnikov.intellij.plugin.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ProjectSettings {
  private static final String PREFIX = "LombokPlugin";

  public static final String IS_LOMBOK_VERSION_CHECK_ENABLED = PREFIX + "IS_LOMBOK_VERSION_CHECK_Enabled";

  public static boolean isEnabled(@NotNull Project project, final String propertyName) {
    return PropertiesComponent.getInstance(project).getBoolean(propertyName, true);
  }

  public static boolean isEnabled(@NotNull Project project, final String propertyName, boolean defaultValue) {
    return PropertiesComponent.getInstance(project).getBoolean(propertyName, defaultValue);
  }

  public static void setEnabled(@NotNull Project project, final String propertyName, boolean value) {
    PropertiesComponent.getInstance(project).setValue(propertyName, String.valueOf(value), "");
  }
}
