// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class ProjectIndexingComponent implements DumbService.DumbModeListener {
  public static final String PROFILE_INDEXING_COMPONENT = "performancePlugin.isProfileIndexing";
  public static final String PROFILE_WITH_ASYNC = "performancePlugin.isProfileIndexingWithAsync";
  private final @NotNull Project project;
  private static final int TIMEOUT = 500;
  private final Alarm alarm;

  ProjectIndexingComponent(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    this.project = project;
    alarm = new Alarm(coroutineScope, Alarm.ThreadToUse.SWING_THREAD);
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

  private void runScriptAfterDumb(@NotNull Project project) {
    DumbService.getInstance(project).smartInvokeLater(() -> {
      alarm.addRequest(() -> {
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
      }, TIMEOUT);
    });
  }

  private boolean isProfilingEnabled() {
    return PropertiesComponent.getInstance(project).getBoolean(PROFILE_INDEXING_COMPONENT);
  }
}
