package de.plushnikov.intellij.plugin;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.options.AnnotationProcessorsConfigurable;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows notifications about project setup issues, that make the plugin not working.
 *
 * @author Alexej Kubarev
 */
public class LombokPluginProjectValidatorComponent extends AbstractProjectComponent {

  private Project project;

  public LombokPluginProjectValidatorComponent(Project project) {
    super(project);
    this.project = project;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "lombok.ProjectValidatorComponent";
  }

  @Override
  public void projectOpened() {
    // If plugin is not enabled - no point to continue
    if (!ProjectSettings.isLombokEnabledInProject(project)) {
      return;
    }

    NotificationGroup group = NotificationGroup.findRegisteredGroup(Version.PLUGIN_NAME);
    if (group == null) {
      group = new NotificationGroup(Version.PLUGIN_NAME, NotificationDisplayType.BALLOON, true);
    }

    // Lombok dependency check
    boolean hasLombokLibrary = hasLombokLibrary(project);

    // If dependency is missing and missing dependency notification setting is enabled (defaults to disabled)
    if (!hasLombokLibrary && ProjectSettings.isEnabled(project, ProjectSettings.IS_MISSING_LOMBOK_CHECK_ENABLED, false)) {
      Notification notification = group.createNotification(LombokBundle.message("config.warn.dependency.missing.title"),
        LombokBundle.message("config.warn.dependency.missing.message", project.getName()),
        NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER);

      Notifications.Bus.notify(notification, project);
    }

    // If dependency is present and out of date notification setting is enabled (defaults to disabled)
    if (hasLombokLibrary && ProjectSettings.isEnabled(project, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false)) {
      final ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        String lombokVersion = parseLombokVersion(findLombokEntry(ModuleRootManager.getInstance(module)));

        if (null != lombokVersion && compareVersionString(lombokVersion, Version.LAST_LOMBOK_VERSION) < 0) {
          Notification notification = group.createNotification(LombokBundle.message("config.warn.dependency.outdated.title"),
            LombokBundle.message("config.warn.dependency.outdated.message", project.getName(), module.getName(), lombokVersion, Version.LAST_LOMBOK_VERSION),
            NotificationType.WARNING, NotificationListener.URL_OPENING_LISTENER);

          Notifications.Bus.notify(notification, project);
        }
      }
    }

    // Annotation Processing check
    boolean annotationProcessorsEnabled = hasAnnotationProcessorsEnabled(project);
    if (!annotationProcessorsEnabled) {

      String annotationProcessorsConfigName = new AnnotationProcessorsConfigurable(project).getDisplayName();

      Notification notification = group.createNotification(LombokBundle.message("config.warn.annotation-processing.disabled.title"),
        LombokBundle.message("config.warn.annotation-processing.disabled.message", project.getName()),
        NotificationType.ERROR,
        new SettingsOpeningListener(project, annotationProcessorsConfigName));

      Notifications.Bus.notify(notification, project);
    }
  }

  private boolean hasAnnotationProcessorsEnabled(Project project) {
    final CompilerConfiguration config = CompilerConfiguration.getInstance(project);
    boolean enabled = true;

    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (ModuleRootManager.getInstance(module).getSourceRoots().length > 0) {
        enabled &= config.getAnnotationProcessingConfiguration(module).isEnabled();
      }
    }

    return enabled;
  }

  private boolean hasLombokLibrary(Project project) {
    PsiPackage lombokPackage = JavaPsiFacade.getInstance(project).findPackage("lombok");

    return lombokPackage != null;
  }

  @Nullable
  private OrderEntry findLombokEntry(@NotNull ModuleRootManager moduleRootManager) {
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry.getPresentableName().contains("lombok")) {
        return orderEntry;
      }
    }
    return null;
  }

  @Nullable
  protected String parseLombokVersion(@Nullable OrderEntry orderEntry) {
    String result = null;
    if (null != orderEntry) {
      final String presentableName = orderEntry.getPresentableName();
      Pattern pattern = Pattern.compile("(.*:)([\\d\\.]+)(.*)");
      final Matcher matcher = pattern.matcher(presentableName);
      if (matcher.find()) {
        result = matcher.group(2);
      }
    }
    return result;
  }

  protected int compareVersionString(@NotNull String firstVersionOne, @NotNull String secondVersion) {
    String[] firstVersionParts = firstVersionOne.split("\\.");
    String[] secondVersionParts = secondVersion.split("\\.");
    int length = Math.max(firstVersionParts.length, secondVersionParts.length);
    for (int i = 0; i < length; i++) {
      int firstPart = i < firstVersionParts.length && !firstVersionParts[i].isEmpty() ?
        Integer.parseInt(firstVersionParts[i]) : 0;
      int secondPart = i < secondVersionParts.length && !secondVersionParts[i].isEmpty() ?
        Integer.parseInt(secondVersionParts[i]) : 0;
      if (firstPart < secondPart) {
        return -1;
      }
      if (firstPart > secondPart) {
        return 1;
      }
    }
    return 0;
  }

  /**
   * Simple {@link NotificationListener.Adapter} that opens Settings Page for correct dialog.
   */
  private static class SettingsOpeningListener extends NotificationListener.Adapter {

    private final Project project;
    private final String nameToSelect;

    SettingsOpeningListener(Project project, String nameToSelect) {
      this.project = project;
      this.nameToSelect = nameToSelect;
    }

    @Override
    protected void hyperlinkActivated(@NotNull final Notification notification, @NotNull final HyperlinkEvent e) {

      if (!project.isDisposed()) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, nameToSelect);
      }
    }
  }
}
