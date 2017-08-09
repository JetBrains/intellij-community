package com.jetbrains.edu.learning;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;

public class PyStudyShowTutorial extends AbstractProjectComponent {

  private static final String ourShowPopup = "StudyShowPopup";
  private final Project myProject;

  protected PyStudyShowTutorial(Project project) {
    super(project);
    myProject = project;
  }

  @Override
  public void projectOpened() {
    ApplicationManager.getApplication().invokeLater((DumbAwareRunnable)() -> ApplicationManager.getApplication().runWriteAction(
      (DumbAwareRunnable)() -> {
        if (PropertiesComponent.getInstance().getBoolean(ourShowPopup, true)) {
          final String content = "<html>If you'd like to learn more about PyCharm Edu, " +
                                 "click <a href=\"https://www.jetbrains.com/pycharm-edu/quickstart/\">here</a> to watch a tutorial</html>";
          final Notification notification = new Notification("Watch Tutorials!", "", content, NotificationType.INFORMATION,
                                                             new NotificationListener.UrlOpeningListener(true));
          StartupManager.getInstance(myProject).registerPostStartupActivity(() -> Notifications.Bus.notify(notification));
          Balloon balloon = notification.getBalloon();
          if (balloon != null) {
            balloon.addListener(new JBPopupAdapter() {
              @Override
              public void onClosed(LightweightWindowEvent event) {
                notification.expire();
              }
            });
          }
          notification.whenExpired(() -> PropertiesComponent.getInstance().setValue(ourShowPopup, false, true));
        }
      }));
  }
}
