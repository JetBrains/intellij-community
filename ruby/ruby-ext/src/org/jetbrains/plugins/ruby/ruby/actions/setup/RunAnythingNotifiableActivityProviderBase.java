package org.jetbrains.plugins.ruby.ruby.actions.setup;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import icons.RubyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;

public abstract class RunAnythingNotifiableActivityProviderBase implements RunAnythingActivityProvider {
  private static final String RUN_ANYTHING_GROUP_ID = RBundle.message("run.anything.custom.activity.notification.group.id");

  /**
   * Executes arbitrary activity if {@code pattern} is matched as {@link #isMatching(DataContext, String)}
   *
   * @param dataContext 'Run Anything' data context
   * @param pattern     'Run Anything' search bar input text
   * @return true if succeed, false is failed
   */
  protected abstract boolean runNotificationProduceActivity(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Creates rollback action for {@link #runNotificationProduceActivity(DataContext, String)}
   *
   * @param dataContext 'Run Anything' data context
   * @return rollback action for {@link #runNotificationProduceActivity(DataContext, String)}, null if isn't provided
   */
  @Nullable
  protected abstract Runnable getRollbackAction(@NotNull DataContext dataContext);

  /**
   * Creates post activity {@link Notification} title
   *
   * @param dataContext 'Run Anything' data context
   * @param pattern     'Run Anything' search bar input text
   */
  @NotNull
  protected abstract String getNotificationTitle(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Creates post activity {@link Notification} content
   *
   * @param dataContext 'Run Anything' data context
   * @param pattern     'Run Anything' search bar input text
   */
  @NotNull
  protected abstract String getNotificationContent(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Executes arbitrary activity in IDE and shows {@link Notification} with optional rollback action
   *
   * @param dataContext 'Run Anything' data context
   * @param pattern     'Run Anything' search bar input text
   * @return true if succeed, false is failed
   */
  @Override
  public boolean runActivity(@NotNull DataContext dataContext, @NotNull String pattern) {
    if (runNotificationProduceActivity(dataContext, pattern)) {
      getNotificationCallback(dataContext, pattern).run();
      return true;
    }
    return false;
  }

  private Runnable getNotificationCallback(@NotNull DataContext dataContext, @NotNull String commandLine) {
    return () -> {
      Notification notification = new Notification(
        RUN_ANYTHING_GROUP_ID,
        RubyIcons.RunAnything.Run_anything,
        getNotificationTitle(dataContext, commandLine),
        null,
        getNotificationContent(dataContext, commandLine),
        NotificationType.INFORMATION,
        null
      );

      Runnable rollbackAction = getRollbackAction(dataContext);
      if (rollbackAction != null) {
        AnAction action = new AnAction(RBundle.message("run.anything.custom.activity.rollback.action")) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            rollbackAction.run();
            notification.expire();
          }
        };
        notification.addAction(action);
      }

      Notifications.Bus.notify(notification);
    };
  }
}