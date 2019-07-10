// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;

public class MavenImportNotifier extends MavenSimpleProjectComponent implements Disposable {
  private static final String MAVEN_IMPORT_NOTIFICATION_GROUP = "Maven Import";

  private MavenProjectsManager myMavenProjectsManager;
  private MergingUpdateQueue myUpdatesQueue;

  private Notification myNotification;

  public MavenImportNotifier(@NotNull Project project) {
    super(project);

    if (!isNormalProject()) return;

    NotificationsConfiguration.getNotificationsConfiguration().register(MAVEN_IMPORT_NOTIFICATION_GROUP,
                                                                        NotificationDisplayType.STICKY_BALLOON,
                                                                        true);

    myMavenProjectsManager = MavenProjectsManager.getInstance(project);

    myUpdatesQueue = new MergingUpdateQueue("MavenImportNotifier", 500, false, MergingUpdateQueue.ANY_COMPONENT, myProject);

    myMavenProjectsManager.addManagerListener(new MavenProjectsManager.Listener() {
      @Override
      public void activated() {
        init();
      }

      @Override
      public void projectsScheduled() {
        scheduleUpdate(false);
      }

      @Override
      public void importAndResolveScheduled() {
        scheduleUpdate(true);
      }
    });
  }

  private void init() {
    myUpdatesQueue.activate();
  }

  @Override
  public void dispose() {
    if (myNotification != null) myNotification.expire();
  }

  private void scheduleUpdate(final boolean close) {
    myUpdatesQueue.queue(new Update(myUpdatesQueue) {
      @Override
      public void run() {
        doUpdateNotifications(close);
      }
    });
  }

  private void doUpdateNotifications(boolean close) {
    if (close) {
      if (myNotification == null) return;

      myNotification.expire();
      myNotification = null;
    }
    else {
      if (myNotification != null && !myNotification.isExpired()) return;

      myNotification = new Notification(
        MAVEN_IMPORT_NOTIFICATION_GROUP, ProjectBundle.message("maven.project.changed"), "", NotificationType.INFORMATION);
      myNotification.addAction(new NotificationAction(ProjectBundle.message("maven.project.importChanged")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          FileDocumentManager.getInstance().saveAllDocuments();
          myMavenProjectsManager.scheduleImportAndResolve(false);
          notification.expire();
          myNotification = null;
        }
      });
      myNotification.addAction(new NotificationAction(ProjectBundle.message("maven.project.enableAutoImport")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          myMavenProjectsManager.getImportingSettings().setImportAutomatically(true);
          notification.expire();
          myNotification = null;
        }
      });
      Notifications.Bus.notify(myNotification, myProject);
    }
  }
}
