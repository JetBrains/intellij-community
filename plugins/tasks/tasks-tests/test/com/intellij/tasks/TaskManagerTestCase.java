/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks;

import com.intellij.openapi.util.Couple;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightCodeInsightFixtureTestCase {
  protected static final SimpleDateFormat SHORT_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  static {
    SHORT_TIMESTAMP_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
  
  protected TaskManager myTaskManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTaskManager = TaskManager.getManager(getProject());
    removeAllTasks();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((TaskManagerImpl)myTaskManager).setRepositories(Collections.<TaskRepository>emptyList());
      removeAllTasks();
    }
    finally {
      myTaskManager = null;
      super.tearDown();
    }
  }

  private void removeAllTasks() {
    List<LocalTask> tasks = myTaskManager.getLocalTasks();
    for (LocalTask task : tasks) {
      myTaskManager.removeTask(task);
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
