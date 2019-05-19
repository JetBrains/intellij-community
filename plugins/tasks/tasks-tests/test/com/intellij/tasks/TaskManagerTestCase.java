// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks;

import com.intellij.openapi.util.Couple;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ReflectionUtil;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightPlatformTestCase {
  protected static final SimpleDateFormat SHORT_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  static {
    SHORT_TIMESTAMP_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  protected TaskManagerImpl myTaskManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTaskManager = (TaskManagerImpl)TaskManager.getManager(getProject());
    myTaskManager.prepareForNextTest();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Thread thread = ReflectionUtil.getField(MultiThreadedHttpConnectionManager.class, null, Thread.class, "REFERENCE_QUEUE_THREAD");
      MultiThreadedHttpConnectionManager.shutdownAll();
      if (thread != null) {
        thread.join();
      }
      myTaskManager.prepareForNextTest();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myTaskManager = null;
      super.tearDown();
    }
  }

  /**
   * @return semi-random duration for a work item in the range [1m, 4h 0m]
   */
  @NotNull
  protected Couple<Integer> generateWorkItemDuration() {
    // semi-unique duration as timeSpend
    // should be no longer than 8 hours in total, because it's considered as one full day
    final int minutes = (int)(System.currentTimeMillis() % 240) + 1;
    return Couple.of(minutes / 60, minutes % 60);
  }
}
