// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.NlsSafe;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ProjectInitializationDiagnosticServiceImpl implements ProjectInitializationDiagnosticService {
  private static final Logger LOG = Logger.getInstance(ProjectInitializationDiagnosticServiceImpl.class);
  private final boolean disabledOutsideIntegrationTests;
  private final ActivityTracker dumbTracker;
  private final Project myProject;
  private final Object LOCK = new Object();
  private final AtomicLong keyNumberCounter = new AtomicLong();
  private final Long2ObjectMap<Supplier<@NotNull @NlsSafe String>> activities = new Long2ObjectOpenHashMap<>();

  public ProjectInitializationDiagnosticServiceImpl(@NotNull Project project) {
    disabledOutsideIntegrationTests = !ApplicationManagerEx.isInIntegrationTest();
    myProject = disabledOutsideIntegrationTests ? null : project;
    dumbTracker = disabledOutsideIntegrationTests ? new ActivityTracker() {
      @Override
      public void activityFinished() {
      }
    } : null;
  }

  @Override
  public ActivityTracker registerBeginningOfInitializationActivity(@NotNull Supplier<@NotNull @NlsSafe String> debugMessageProducer) {
    if (disabledOutsideIntegrationTests) return dumbTracker;

    long keyNumber = keyNumberCounter.getAndIncrement();
    synchronized (LOCK) {
      activities.put(keyNumber, debugMessageProducer);
    }
    return new MyActivityTracker(keyNumber);
  }

  @Override
  public boolean isProjectInitializationAndIndexingFinished() {
    if (disabledOutsideIntegrationTests) return true;

    if (!StartupManager.getInstance(myProject).postStartupActivityPassed()) {
      LOG.info("Project startup activities are not finished yet.");
      return false;
    }
    Supplier<@NotNull @NlsSafe String> nextMessage;
    synchronized (LOCK) {
      if (activities.isEmpty()) return true;
      nextMessage = activities.values().iterator().next();
    }
    LOG.info("Project initialization & indexing not finished. " + nextMessage.get() + " is still running");
    return false;
  }

  private class MyActivityTracker implements ActivityTracker {
    private final long code;

    private MyActivityTracker(long code) {
      this.code = code;
    }

    @Override
    public void activityFinished() {
      synchronized (LOCK) {
        activities.remove(code);
      }
    }
  }
}
