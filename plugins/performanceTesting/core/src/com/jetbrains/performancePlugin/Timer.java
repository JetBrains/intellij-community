// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.util.ConcurrencyUtil;
import com.sun.management.OperatingSystemMXBean;
import io.opentelemetry.api.trace.Span;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import javax.swing.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

public final class Timer {
  public static final Timer instance = new Timer();

  private String myActivityName = "default";
  private volatile long myStartTime;
  private volatile long myStopTime;
  private LongAccumulator myLongestDelay;
  private volatile double myHighestCPULoad;
  private volatile long myHighestRAMUsage;
  private final AtomicLong myTotalDelay = new AtomicLong();
  private final AtomicLong myTotalCPULoad = new AtomicLong();
  private final AtomicLong myTotalRAMUsage = new AtomicLong();
  private final AtomicLong myCounter = new AtomicLong();
  private final AtomicLong myHWCounter = new AtomicLong();
  private Span mySpan;
  private ScheduledExecutorService executor;
  private long totalTime;

  public void start() {
    start("default", true);
  }

  public void start(String activityName, boolean withSpan) {
    myActivityName = activityName;
    myStartTime = System.currentTimeMillis();
    myLongestDelay = new LongAccumulator(Math::max, 0);
    myHighestCPULoad = 0;
    myHighestRAMUsage = 0;
    myTotalRAMUsage.set(0);
    myTotalCPULoad.set(0);
    myTotalDelay.set(0);
    myCounter.set(0);
    myHWCounter.set(0);
    executor = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin timer");

    SystemInfo info = new SystemInfo();
    int processId = info.getOperatingSystem().getProcessId();
    OSProcess process = info.getOperatingSystem().getProcess(processId);
    executor.scheduleWithFixedDelay(() -> {
      long before = System.currentTimeMillis();
      try {
        SwingUtilities.invokeAndWait(() -> {
        });
        long after = System.currentTimeMillis();
        long delay = after - before;
        myTotalDelay.addAndGet(delay);
        myCounter.incrementAndGet();
        myLongestDelay.accumulate(delay);
      }
      catch (InterruptedException | InvocationTargetException ignored) {
      }
    }, 0, 50, TimeUnit.MILLISECONDS);

    OperatingSystemMXBean bean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    executor.scheduleWithFixedDelay(() -> {
      myHWCounter.incrementAndGet();
      double cpuLoad = bean.getProcessCpuLoad();
      if (cpuLoad > 0 && cpuLoad <= 1) {
        myTotalCPULoad.addAndGet(Math.round(cpuLoad * 100));
        if (myHighestCPULoad < cpuLoad) myHighestCPULoad = cpuLoad;
      }

      long ramUsage = process.getResidentSetSize();
      myTotalRAMUsage.addAndGet(ramUsage);
      if (myHighestRAMUsage < ramUsage) myHighestRAMUsage = ramUsage;
    }, 0, 1, TimeUnit.SECONDS);

    if(withSpan) {
      String spanName = activityName.equals("default") ? "timer" : activityName;
      // We need to set parent or null since it's part of general startup activity
      //noinspection DataFlowIssue Parent can be null.
      mySpan = PerformanceTestSpan.TRACER.spanBuilder(spanName).setParent(PerformanceTestSpan.getContext()).startSpan();
    }
  }

  public void stop() {
    if(mySpan != null) {
      mySpan.setAttribute("max_awt_delay", getLongestDelay());
      mySpan.setAttribute("average_awt_delay", getAverageDelay());
      mySpan.setAttribute("max_cpu_load", Math.round(myHighestCPULoad * 100));
      mySpan.setAttribute("average_cpu_load", Math.round(getAverageCPULoad()));
      mySpan.end();
    }
    myStopTime = System.currentTimeMillis();
    totalTime = myStopTime - myStartTime;
    executor.shutdownNow();
  }

  public void reportToTeamCity() {
    logValue(myActivityName, totalTime);
    logValue(myActivityName + " | Total Time Execution", totalTime);
    logValue(myActivityName + " | Responsiveness", getLongestDelay());
    logValue(myActivityName + " | Average Responsiveness", getAverageDelay());
    logValue(myActivityName + " | Average RAM Usage (%)", getAverageRAMUsage());
    logValue(myActivityName + " | Average CPU Usage", getAverageCPULoad());
    logValue(myActivityName + " | Max RAM Usage", myHighestRAMUsage);
    logValue(myActivityName + " | Max CPU Usage", myHighestCPULoad);
  }

  public long getLongestDelay() {
    return myLongestDelay.get();
  }

  public long getTotalTime() {
    return totalTime;
  }

  public long getStopTime() {
    return myStopTime;
  }

  public double getAverageCPULoad() {
    long counterValue = myHWCounter.get();
    return counterValue != 0 ? (double) myTotalCPULoad.get() / counterValue : 0.0;
  }

  public int getAverageRAMUsage() {
    long counterValue = myHWCounter.get();
    return counterValue != 0 ? (int)(myTotalRAMUsage.get() / counterValue) : 0;
  }

  public long getAverageDelay() {
    long counterValue = myCounter.get();
    return counterValue != 0 ? myTotalDelay.get() / counterValue : 0;
  }

  public String getActivityName() {
    return myActivityName;
  }

  public boolean isStarted() {
    return executor != null && !executor.isShutdown();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logValue(String key, long value) {
    if (value >= 0L) {
      System.out.println("##teamcity[buildStatisticValue key='" + key + "' value='" + value + "']");
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logValue(String key, double value) {
    if (value >= 0L) {
      System.out.println("##teamcity[buildStatisticValue key='"+ key + "' value='" + value + "']");
    }
  }
}
