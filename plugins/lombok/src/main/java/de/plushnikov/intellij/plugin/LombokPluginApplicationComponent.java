package de.plushnikov.intellij.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import de.plushnikov.intellij.plugin.settings.LombokSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Main application component, that loads Lombok support
 */
public class LombokPluginApplicationComponent implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(LombokPluginApplicationComponent.class.getName());

  /**
   * Plugin has been updated with the current run.
   */
  private boolean updated;

  /**
   * Plugin update notification has been shown.
   */
  private boolean updateNotificationShown;

  /**
   * Checks if plugin was updated in the current run.
   *
   * @return plugin was updated
   */
  public boolean isUpdated() {
    return updated;
  }

  public boolean isUpdateNotificationShown() {
    return updateNotificationShown;
  }

  public void setUpdateNotificationShown(boolean shown) {
    this.updateNotificationShown = shown;
  }

  /**
   * Get LombokPlugin Application Component
   *
   * @return LombokPlugin Application Component
   */
  public static LombokPluginApplicationComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(LombokPluginApplicationComponent.class);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Lombok plugin for IntelliJ";
  }

  @Override
  public void initComponent() {
    LOG.info("Lombok plugin initialized for IntelliJ");

    final LombokSettings settings = LombokSettings.getInstance();
    updated = !Version.PLUGIN_VERSION.equals(settings.getVersion());
    if (updated) {
      settings.setVersion(Version.PLUGIN_VERSION);
    }
  }

  @Override
  public void disposeComponent() {
    LOG.info("Lombok plugin disposed for IntelliJ");
  }

}
