package de.plushnikov.intellij.plugin.activity;

import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Shows notifications about project setup issues, that make the plugin not working.
 *
 * @author Alexej Kubarev
 */
public class LombokProjectValidatorActivity implements StartupActivity.DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    // enable annotationProcessing check
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(BuildManagerListener.TOPIC, new LombokBuildManagerListener());

    if (ProjectSettings.isEnabled(project, ProjectSettings.IS_LOMBOK_VERSION_CHECK_ENABLED, false)) {
      ReadAction.nonBlocking(() -> {
          if (project.isDisposed()) return null;

          final boolean hasLombokLibrary = LombokLibraryUtil.hasLombokLibrary(project);

          // If dependency is present and out of date notification setting is enabled (defaults to disabled)
          if (hasLombokLibrary) {
            String lombokVersion = LombokLibraryUtil.getLombokVersionCached(project);

            if (StringUtil.isNotEmpty(lombokVersion) && Version.isLessThan(lombokVersion, Version.LAST_LOMBOK_VERSION)) {
              return getNotificationGroup().createNotification(
                LombokBundle.message("config.warn.dependency.outdated.title"),
                LombokBundle.message("config.warn.dependency.outdated.message", project.getName(), lombokVersion, Version.LAST_LOMBOK_VERSION),
                NotificationType.WARNING);
            }
          }
          return null;
        }).expireWith(LombokPluginDisposable.getInstance(project))
        .coalesceBy(project, LombokProjectValidatorActivity.class)
        .finishOnUiThread(ModalityState.NON_MODAL, notification -> {
          if (notification != null) {
            notification.setListener(NotificationListener.URL_OPENING_LISTENER).notify(project);
            Disposer.register(LombokPluginDisposable.getInstance(project), notification::expire);
          }
        }).submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  @NotNull
  private static NotificationGroup getNotificationGroup() {
    return NotificationGroupManager.getInstance().getNotificationGroup(Version.PLUGIN_NAME);
  }
}
