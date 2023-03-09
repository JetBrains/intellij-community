package com.jetbrains.performancePlugin.commands;

import com.intellij.util.ConcurrencyUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class Waiter {
  private static final long DELAY = 100L;

  public static CountDownLatch checkCondition(BooleanSupplier function) {
    CountDownLatch latch = new CountDownLatch(1);
    ScheduledExecutorService executor = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin waiter");
    executor.scheduleWithFixedDelay(() -> {
      if (function.getAsBoolean()) {
        latch.countDown();
        executor.shutdown();
      }
    }, 0, DELAY, TimeUnit.MILLISECONDS);
    return latch;
  }
}
