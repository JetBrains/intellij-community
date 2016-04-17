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

    NotificationGroup group = new NotificationGroup(Version.PLUGIN_NAME, NotificationDisplayType.BALLOON, true);

    // Lombok dependency check
    boolean hasLombokLibrary = hasLombokLibrary(project);
    if (!hasLombokLibrary) {
      Notification notification = group.createNotification(LombokBundle.message("config.warn.dependency.missing.title"),
          LombokBundle.message("config.warn.dependency.missing.message", project.getName()),
          NotificationType.ERROR,
          new NotificationListener.UrlOpeningListener(false));

      Notifications.Bus.notify(notification, project);
    }

    if (hasLombokLibrary) {
      final ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        final OrderEntry lombokEntry = findLombokEntry(ModuleRootManager.getInstance(module));
        final String lombokVersion = parseLombokVersion(lombokEntry);

        if (null != lombokVersion && compareVersionString(lombokVersion, Version.LAST_LOMBOK_VERSION) < 0) {
          Notification notification = group.createNotification(LombokBundle.message("config.warn.dependency.outdated.title"),
              LombokBundle.message("config.warn.dependency.outdated.message", project.getName(), module.getName(), lombokVersion, Version.LAST_LOMBOK_VERSION),
              NotificationType.WARNING, null);

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
  private String parseLombokVersion(@Nullable OrderEntry orderEntry) {
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

  private int compareVersionString(@NotNull String versionOne, @NotNull String versionTwo) {
    String[] thisParts = versionOne.split("\\.");
    String[] thatParts = versionTwo.split("\\.");
    int length = Math.max(thisParts.length, thatParts.length);
    for (int i = 0; i < length; i++) {
      int thisPart = i < thisParts.length ?
          Integer.parseInt(thisParts[i]) : 0;
      int thatPart = i < thatParts.length ?
          Integer.parseInt(thatParts[i]) : 0;
      if (thisPart < thatPart) {
        return -1;
      }
      if (thisPart > thatPart) {
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
      ShowSettingsUtil.getInstance().showSettingsDialog(project, nameToSelect);
    }
  }
}
