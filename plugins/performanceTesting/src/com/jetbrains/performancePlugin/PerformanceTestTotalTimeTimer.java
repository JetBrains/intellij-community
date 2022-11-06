package com.jetbrains.performancePlugin;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public final class PerformanceTestTotalTimeTimer implements StartupActivity.DumbAware {
  public static final String TOTAL_TEST_TIMER_NAME = "test";

  @Override
  public void runActivity(@NotNull Project project) {
    if (ProjectLoaded.TEST_SCRIPT_FILE_PATH != null) {
      Timer myTimer = new Timer();
      myTimer.start(TOTAL_TEST_TIMER_NAME, true);
      MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
      connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void appWillBeClosed(boolean isRestart) {
          myTimer.stop();
        }
      });
    }
  }
}
