package de.plushnikov.intellij.plugin;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.options.AnnotationProcessorsConfigurable;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.service.project.manage.ModuleDependencyDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiPackage;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

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
    return "lombok.ProjectValidatiorComponent";
  }

  @Override
  public void projectOpened() {

    // If plugin is not enabled - no point to continue
    if (!ProjectSettings.isLombokEnabledInProject(project)) {
      return;
    }

    NotificationGroup group = new NotificationGroup("Lombok Plugin", NotificationDisplayType.BALLOON, true);

    // Lombok dependency check
    boolean hasLombokLibrary = hasLombokLibrary(project);
    if (!hasLombokLibrary) {
      Notification notification = group.createNotification(LombokBundle.message("config.warn.dependency.missing.title", ""),
          LombokBundle.message("config.warn.dependency.missing.message", project.getName()),
          NotificationType.ERROR,
          new NotificationListener.UrlOpeningListener(false));

      Notifications.Bus.notify(notification, project);
    }

    // Annotation Processing check
    boolean annotationProcessorsEnabled = hasAnnotationProcessorsEnabled(project, true);
    if (!annotationProcessorsEnabled) {

      String annotationProcessorsConfigName = new AnnotationProcessorsConfigurable(project).getDisplayName();

      Notification notification = group.createNotification(LombokBundle.message("config.warn.annotation-processing.disabled.title", ""),
          LombokBundle.message("config.warn.annotation-processing.disabled.message", project.getName()),
          NotificationType.ERROR,
          new SettingsOpeningListener(project, annotationProcessorsConfigName));

      Notifications.Bus.notify(notification, project);
    }


  }

  private boolean hasAnnotationProcessorsEnabled(Project project, boolean checkModules) {
    final CompilerConfiguration config = CompilerConfiguration.getInstance(project);
    boolean enabled = config.isAnnotationProcessorsEnabled();

    if (!checkModules) {
      return enabled;
    }

    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      enabled &= config.getAnnotationProcessingConfiguration(module).isEnabled();

    }

    return enabled;
  }

  private boolean hasLombokLibrary(Project project) {

    PsiPackage lombokPackage = JavaPsiFacade.getInstance(project).findPackage("lombok");

    return lombokPackage != null;
  }

  /**
   * Simple {@link NotificationListener.Adapter} that opens Settings Page for correct dialog.
   */
  private static class SettingsOpeningListener extends NotificationListener.Adapter {

    private Project project;
    private String nameToSelect;

    public SettingsOpeningListener(Project project, String nameToSelect) {
      this.project = project;
      this.nameToSelect = nameToSelect;
    }

    @Override
    protected void hyperlinkActivated(@NotNull final Notification notification, @NotNull final HyperlinkEvent e) {
      ShowSettingsUtil.getInstance()
          .showSettingsDialog(project, nameToSelect);
    }
  }
}
