package com.jetbrains.performancePlugin;

import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ProjectInitializationDiagnosticServiceImpl implements ProjectInitializationDiagnosticService {
  private static final Logger LOG = Logger.getInstance(ProjectInitializationDiagnosticServiceImpl.class);
  private final Object LOCK = new Object();
  private final AtomicLong keyNumberCounter = new AtomicLong();
  private final Long2ObjectMap<Supplier<@NotNull @NlsSafe String>> activities = new Long2ObjectOpenHashMap<>();

  @Override
  public ActivityTracker registerBeginningOfInitializationActivity(@NotNull Supplier<@NotNull @NlsSafe String> debugMessageProducer) {
    long keyNumber = keyNumberCounter.getAndIncrement();
    synchronized (LOCK) {
      activities.put(keyNumber, debugMessageProducer);
    }
    return new MyActivityTracker(keyNumber);
  }

  @Override
  public boolean isProjectInitializationAndIndexingFinished() {
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
      activities.remove(code);
    }
  }
}
