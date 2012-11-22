package com.intellij.tasks.timeTracking;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.*;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: Evgeny.Zakrevsky
 * Date: 11/19/12
 */

@State(
  name = "TimeTrackingManager",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class TimeTrackingManager implements ProjectComponent, PersistentStateComponent<TimeTrackingManager.Config> {

  public static final int TIME_TRACKING_TIME_UNIT = 1000;

  private final Project myProject;
  private final TaskManager myTaskManager;
  private final Config myConfig = new Config();
  private Timer myTimeTrackingTimer;
  private Alarm myIdleAlarm;

  public TimeTrackingManager(Project project,
                             TaskManager taskManager) {
    myProject = project;
    myTaskManager = taskManager;
  }

  public static TimeTrackingManager getInstance(Project project) {
    return project.getComponent(TimeTrackingManager.class);
  }

  private void startTimeTrackingTimer() {
    if (!myTimeTrackingTimer.isRunning()) {
      myTimeTrackingTimer.start();
    }

    myIdleAlarm.cancelAllRequests();
    myIdleAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myTimeTrackingTimer.isRunning()) {
          myTimeTrackingTimer.stop();
        }
      }
    }, getState().suspendDelayInSeconds * 1000);
  }

  public void updateTimeTrackingToolWindow() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.TASKS);
    if (isTimeTrackingToolWindowAvailable()) {
      if (toolWindow == null) {
        toolWindow =
          ToolWindowManager.getInstance(myProject).registerToolWindow(ToolWindowId.TASKS, true, ToolWindowAnchor.RIGHT, myProject, true);
        new TasksToolWindowFactory().createToolWindowContent(myProject, toolWindow);
        final ToolWindow finalToolWindow = toolWindow;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            finalToolWindow.setAvailable(true, null);
            finalToolWindow.show(null);
            finalToolWindow.activate(null);
          }
        });
      }
    }
    else {
      if (toolWindow != null) {
        final ToolWindow finalToolWindow = toolWindow;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            finalToolWindow.setAvailable(false, null);
          }
        });
      }
    }
  }

  public boolean isTimeTrackingToolWindowAvailable() {
    return getState().enabled;
  }

  public boolean isTimeTrackingAutoMode() {
    return getState().autoMode;
  }

  public void setTimeTrackingAutoMode(final boolean state) {
    getState().autoMode = state;
  }

  public boolean isHideClosedTasks() {
    return getState().hideClosedTasks;
  }

  public void setHideClosedTasks(final boolean state) {
    getState().hideClosedTasks = state;
  }

  @Override
  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myTimeTrackingTimer = UIUtil.createNamedTimer("TaskManager time tracking", TIME_TRACKING_TIME_UNIT, new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          final LocalTask activeTask = myTaskManager.getActiveTask();
          if (isTimeTrackingAutoMode()) {
            activeTask.setTimeSpent(activeTask.getTimeSpent() + TIME_TRACKING_TIME_UNIT);
            getState().totallyTimeSpent += TIME_TRACKING_TIME_UNIT;
          }
          else {
            if (activeTask.isRunning()) {
              activeTask.setTimeSpent(activeTask.getTimeSpent() + TIME_TRACKING_TIME_UNIT);
              getState().totallyTimeSpent += TIME_TRACKING_TIME_UNIT;
            }
          }
        }
      });
      StartupManager.getInstance(myProject).registerStartupActivity(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              startTimeTrackingTimer();
            }
          });
        }
      });

      myIdleAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProject);

      IdeEventQueue.getInstance().addActivityListener(new Runnable() {
        @Override
        public void run() {
          final IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
          if (frame == null) return;
          final Project project = frame.getProject();
          if (project == null || !myProject.equals(project)) return;
          startTimeTrackingTimer();
        }
      }, myProject);
    }
  }

  @Override
  public void disposeComponent() {
    if (myTimeTrackingTimer != null) {
      myTimeTrackingTimer.stop();
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Time Tracking Manager";
  }

  @NotNull
  @Override
  public TimeTrackingManager.Config getState() {
    return myConfig;
  }

  @Override
  public void loadState(final TimeTrackingManager.Config state) {
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  public static class Config {
    public boolean enabled = true;
    public long totallyTimeSpent = 0;
    public int suspendDelayInSeconds = 600;
    public boolean autoMode = true;
    public boolean hideClosedTasks = true;
  }
}
