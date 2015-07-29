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

import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManagerTestCase extends LightCodeInsightFixtureTestCase {
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
    }
    super.tearDown();
  }

  private void removeAllTasks() {
    List<LocalTask> tasks = myTaskManager.getLocalTasks();
    for (LocalTask task : tasks) {
      myTaskManager.removeTask(task);
    }
  }
}
