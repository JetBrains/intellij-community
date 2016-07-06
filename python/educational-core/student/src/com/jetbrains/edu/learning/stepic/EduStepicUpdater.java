package com.jetbrains.edu.learning.stepic;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EduStepicUpdater {
  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;

  private final Runnable myCheckRunnable = () -> updateCourseList().doWhenDone(() -> queueNextCheck(CHECK_INTERVAL));
  private final Alarm myCheckForUpdatesAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public EduStepicUpdater(@NotNull Application application) {
    scheduleCourseListUpdate(application);
  }

  public void scheduleCourseListUpdate(Application application) {
    if (!checkNeeded()) {
      return;
    }
    application.getMessageBus().connect(application).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {

        long timeToNextCheck = StepicUpdateSettings.getInstance().getLastTimeChecked() + CHECK_INTERVAL - System.currentTimeMillis();
        if (timeToNextCheck <= 0) {
          myCheckRunnable.run();
        }
        else {
          queueNextCheck(timeToNextCheck);
        }
      }
    });
  }

  private static ActionCallback updateCourseList() {
    ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final List<CourseInfo> courses = EduStepicConnector.getCourses();
      StudyProjectGenerator.flushCache(courses);
      StepicUpdateSettings.getInstance().setLastTimeChecked(System.currentTimeMillis());
    });
    return callback;
  }

  private void queueNextCheck(long interval) {
    myCheckForUpdatesAlarm.addRequest(myCheckRunnable, interval);
  }

  private static boolean checkNeeded() {
    final List<CourseInfo> courses = StudyProjectGenerator.getCoursesFromCache();
    long timeToNextCheck = StepicUpdateSettings.getInstance().getLastTimeChecked() + CHECK_INTERVAL - System.currentTimeMillis();
    return courses.isEmpty() || timeToNextCheck <= 0;
  }
}
