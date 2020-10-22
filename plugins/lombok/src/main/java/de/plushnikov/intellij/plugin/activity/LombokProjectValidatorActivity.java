package de.plushnikov.intellij.plugin.activity;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.notification.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.AppExecutorUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.provider.LombokProcessorProvider;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

/**
 * Shows notifications about project setup issues, that make the plugin not working.
 *
 * @author Alexej Kubarev
 */
public class LombokProjectValidatorActivity implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    // If plugin is not enabled - no point to continue
    if (!ProjectSettings.isLombokEnabledInProject(project)) {
      return;
    }

    LombokProcessorProvider lombokProcessorProvider = LombokProcessorProvider.getInstance(project);
    ReadAction.nonBlocking(() -> {
      if (project.isDisposed()) return null;

      final boolean hasLombokLibrary = hasLombokLibrary(project);

      // If dependency is missing and missing dependency notification setting is enabled (defaults to disabled)
      if (!hasLombokLibrary && ProjectSettings.isEnabled(project, ProjectSettings.IS_MISSING_LOMBOK_CHECK_ENABLED, false)) {
        return getNotificationGroup().createNotification(LombokBundle.message("config.warn.dependency.missing.title"),
                                                         LombokBundle.message("config.warn.dependency.missing.message", project.getName()),
                                                         NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER);
      }

      // If dependency is present and out of date notification setting is enabled (defaults to disabled)
      if (hasLombokLibrary && ProjectSettings.isEnabled(project, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false)) {
        final ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
          String lombokVersion = Version.parseLombokVersion(findLombokEntry(ModuleRootManager.getInstance(module)));

          if (null != lombokVersion && Version.compareVersionString(lombokVersion, Version.LAST_LOMBOK_VERSION) < 0) {
            return getNotificationGroup().createNotification(LombokBundle.message("config.warn.dependency.outdated.title"),
                                                             LombokBundle
                                                               .message("config.warn.dependency.outdated.message", project.getName(),
                                                                        module.getName(), lombokVersion, Version.LAST_LOMBOK_VERSION),
                                                             NotificationType.WARNING, NotificationListener.URL_OPENING_LISTENER);
          }
        }
      }

      // Annotation Processing check
      if (hasLombokLibrary &&
          ProjectSettings.isEnabled(project, ProjectSettings.IS_ANNOTATION_PROCESSING_CHECK_ENABLED, true) &&
          !hasAnnotationProcessorsEnabled(project)) {
        return getNotificationGroup()
          .createNotification(LombokBundle.message("config.warn.annotation-processing.disabled.title"),
                              LombokBundle.message("config.warn.annotation-processing.disabled.message", project.getName()),
                              NotificationType.ERROR,
                              (not, e) -> {
                                if (e.getEventType() ==
                                    HyperlinkEvent.EventType.ACTIVATED) {
                                  enableAnnotations(project);
                                  not.expire();
                                }
                              });
      }
      return null;
    }).expireWith(lombokProcessorProvider)
      .finishOnUiThread(ModalityState.NON_MODAL, notification -> {
        if (notification != null) {
          Notifications.Bus.notify(notification, project);
          Disposer.register(lombokProcessorProvider, () -> notification.expire());
        }
      }).submit(AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private static NotificationGroup getNotificationGroup() {
    NotificationGroup group = NotificationGroup.findRegisteredGroup(Version.PLUGIN_NAME);
    if (group == null) {
      group = new NotificationGroup(Version.PLUGIN_NAME, NotificationDisplayType.BALLOON, true);
    }
    return group;
  }

  private static void enableAnnotations(Project project) {
    CompilerConfigurationImpl compilerConfiguration = getCompilerConfiguration(project);
    compilerConfiguration.getDefaultProcessorProfile().setEnabled(true);
    compilerConfiguration.getModuleProcessorProfiles().forEach(pp -> pp.setEnabled(true));

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(
        LombokBundle.message("popup.content.java.annotation.processing.has.been.enabled"),
        MessageType.INFO,
        null
      )
      .setFadeoutTime(3000)
      .createBalloon()
      .show(RelativePoint.getNorthEastOf(statusBar.getComponent()), Balloon.Position.atRight);
  }

  private static CompilerConfigurationImpl getCompilerConfiguration(Project project) {
    return (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
  }

  private static boolean hasAnnotationProcessorsEnabled(Project project) {
    final CompilerConfigurationImpl compilerConfiguration = getCompilerConfiguration(project);
    return compilerConfiguration.getDefaultProcessorProfile().isEnabled() &&
           compilerConfiguration.getModuleProcessorProfiles().stream().allMatch(AnnotationProcessingConfiguration::isEnabled);
  }

  public static boolean hasLombokLibrary(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      PsiPackage aPackage = ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findPackage("lombok.experimental"));
      return new CachedValueProvider.Result<>(aPackage, ProjectRootManager.getInstance(project));
    }) != null;
  }

  public static boolean isVersionLessThan1_18_16(Project project) {
    if (ProjectSettings.isLombokEnabledInProject(project) && hasLombokLibrary(project)) {
      return CachedValuesManager.getManager(project)
        .getCachedValue(project, () -> {
          Boolean isVersionLessThan = ReadAction.compute(() -> isVersionLessThan1_18_16_Internal(project));
          return new CachedValueProvider.Result<>(isVersionLessThan, ProjectRootManager.getInstance(project));
        });
    }
    return false;
  }

  private static boolean isVersionLessThan1_18_16_Internal(@NotNull Project project) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("lombok.experimental");
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories();
      if (directories.length > 0) {
        List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(directories[0].getVirtualFile());
        if (!entries.isEmpty()) {
          return Version.isLessThan(entries.get(0), "1.18.16");
        }
      }
    }
    return false;
  }

  @Nullable
  private static OrderEntry findLombokEntry(@NotNull ModuleRootManager moduleRootManager) {
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry.getPresentableName().contains("lombok")) {
        return orderEntry;
      }
    }
    return null;
  }
}
