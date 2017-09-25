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

public class PyStudyShowTutorial extends AbstractProjectComponent {

  private static final String POPUP_SHOWN = "StudyShowPopup";
  private final Project myProject;

  protected PyStudyShowTutorial(Project project) {
    super(project);
    myProject = project;
  }

  @Override
  public void projectOpened() {
    if (PropertiesComponent.getInstance().isValueSet(POPUP_SHOWN)) {
      return;
    }
    ApplicationManager.getApplication().invokeLater((DumbAwareRunnable)() -> ApplicationManager.getApplication().runWriteAction(
      (DumbAwareRunnable)() -> {
          final String content = "<html>If you'd like to learn more about PyCharm Edu, " +
                                 "click <a href=\"https://www.jetbrains.com/pycharm-edu/quickstart/\">here</a> to watch a tutorial</html>";
          final Notification notification = new Notification("Watch Tutorials!", "", content, NotificationType.INFORMATION,
                                                             new NotificationListener.UrlOpeningListener(true));
          StartupManager.getInstance(myProject).registerPostStartupActivity(() -> {
            Notifications.Bus.notify(notification);
            PropertiesComponent.getInstance().setValue(POPUP_SHOWN, true);
          });
      }));
  }
}
