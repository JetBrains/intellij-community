// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.profilers;

public final class ProfilersController {

  private boolean stoppedByScript = false;
  private Profiler currentProfilerHandler = null;
  private static ProfilersController instance;
  private String reportsPath;

  private ProfilersController() { }

  public static synchronized ProfilersController getInstance() {
    if (instance == null) {
      instance = new ProfilersController();
    }
    return instance;
  }

  public void setCurrentProfiler(Profiler profilerHandler) {
    currentProfilerHandler = profilerHandler;
    stoppedByScript = false;
    reportsPath = null;
  }

  public Profiler getCurrentProfilerHandler() {
    if (currentProfilerHandler == null) {
      currentProfilerHandler = Profiler.getCurrentProfilerHandler();
    }
    return currentProfilerHandler;
  }

  public boolean isStoppedByScript() {
    return stoppedByScript;
  }

  public void setStoppedByScript(boolean stoppedByScript) {
    this.stoppedByScript = stoppedByScript;
  }

  public String getReportsPath() {
    return reportsPath;
  }

  public void setReportsPath(String reportsPath) {
    this.reportsPath = reportsPath;
  }
}
