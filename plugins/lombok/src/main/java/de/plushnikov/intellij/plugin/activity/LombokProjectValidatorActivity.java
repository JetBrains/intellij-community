package de.plushnikov.intellij.plugin.activity;

import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.notification.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.provider.LombokProcessorProvider;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows notifications about project setup issues, that make the plugin not working.
 *
 * @author Alexej Kubarev
 */
public class LombokProjectValidatorActivity implements StartupActivity.DumbAware {
  private static final Logger LOG = Logger.getInstance(LombokProjectValidatorActivity.class);

  @Override
  public void runActivity(@NotNull Project project) {
    // enable annotationProcessing check
    final SimpleMessageBusConnection connection = project.getMessageBus().simpleConnect();
    connection.subscribe(BuildManagerListener.TOPIC, new LombokBuildManagerListener());

    LombokProcessorProvider lombokProcessorProvider = LombokProcessorProvider.getInstance(project);
    ReadAction.nonBlocking(() -> {
      if (project.isDisposed()) return null;

      final boolean hasLombokLibrary = LombokLibraryUtil.hasLombokLibrary(project);

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
    return NotificationGroupManager.getInstance().getNotificationGroup(Version.PLUGIN_NAME);
  }

  public static boolean isVersionLessThan1_18_16(Project project) {
    return CachedValuesManager.getManager(project)
      .getCachedValue(project, () -> {
        Boolean isVersionLessThan;
        try {
          isVersionLessThan = ReadAction.nonBlocking(
            () -> isVersionLessThanInternal(project, Version.LAST_LOMBOK_VERSION_WITH_JPS_FIX)).executeSynchronously();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          isVersionLessThan = false;
          LOG.error(e);
        }
        return new CachedValueProvider.Result<>(isVersionLessThan, ProjectRootManager.getInstance(project));
      });
  }

  private static boolean isVersionLessThanInternal(@NotNull Project project, @NotNull String version) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("lombok.experimental");
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories();
      if (directories.length > 0) {
        List<OrderEntry> entries =
          ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(directories[0].getVirtualFile());
        if (!entries.isEmpty()) {
          return Version.isLessThan(entries.get(0), version);
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
