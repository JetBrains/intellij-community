package com.jetbrains.performancePlugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.jetbrains.performancePlugin.profilers.Profiler;
import com.jetbrains.performancePlugin.profilers.ProfilersController;
import com.jetbrains.performancePlugin.ui.FinishScriptDialog;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class ProjectIndexingComponent implements DumbService.DumbModeListener {
  public static final String PROFILE_INDEXING_COMPONENT = "performancePlugin.isProfileIndexing";
  public static final String PROFILE_WITH_ASYNC = "performancePlugin.isProfileIndexingWithAsync";
  @NotNull private final Project project;
  private static final int TIMEOUT = 500;
  private final Alarm myAlarm = new Alarm();

  ProjectIndexingComponent(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void enteredDumbMode() {
    try {
      if (isProfilingEnabled()) {
        PropertiesComponent.getInstance(project).setValue(PROFILE_INDEXING_COMPONENT, false);
        PropertiesComponent.getInstance(project).setValue(PROFILE_WITH_ASYNC, false);
        Profiler.getCurrentProfilerHandler(project).startProfiling(project.getName(), new ArrayList<>());
        DumbService.getInstance(project).runReadActionInSmartMode(()->{
          runScriptAfterDumb(project);
        });
      }
    }
    catch (Exception e) {
      new ActionCallbackProfilerStopper().reject(e.getMessage());
    }
  }

  private void runScriptAfterDumb(Project project) {
    DumbService.getInstance(project).smartInvokeLater(() -> myAlarm.addRequest(() -> {
      if (DumbService.isDumb(project)) {
        runScriptAfterDumb(project);
      }
      else {
        ProfilersController.getInstance();
        if (Profiler.isAnyProfilingStarted()) {
          try {
            ProfilersController.getInstance().getCurrentProfilerHandler().stopProfiling(new ArrayList<>());
            ProfilersController.getInstance().setStoppedByScript(true);
          }
          catch (Exception e) {
            new ActionCallbackProfilerStopper().setRejected();
          }
          ApplicationManager.getApplication().invokeLater(() -> new FinishScriptDialog(project).show());
        }
      }
    }, TIMEOUT));
  }

  private boolean isProfilingEnabled() {
    return PropertiesComponent.getInstance(project).getBoolean(PROFILE_INDEXING_COMPONENT);
  }
}
