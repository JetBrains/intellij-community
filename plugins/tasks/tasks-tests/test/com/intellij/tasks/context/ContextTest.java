/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.context;

import com.intellij.tasks.TaskManagerTestCase;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ContextTest extends TaskManagerTestCase {

  public void testSaveContext() throws Exception {
    WorkingContextManager manager = getContextManager();

    manager.saveContext("first", "comment");
    manager.clearContext();
    manager.loadContext("first");

    manager.saveContext(myTaskManager.getActiveTask());
    manager.clearContext();
    manager.restoreContext(myTaskManager.getActiveTask());
  }

  public void testPack() throws Exception {
    WorkingContextManager contextManager = getContextManager();
    for (int i = 0; i < 5; i++) {
      contextManager.saveContext("context" + Integer.toString(i), null);
      Thread.sleep(2000);
    }
    List<ContextInfo> history = contextManager.getContextHistory();
    ContextInfo first = history.get(0);
    System.out.println(first.date);
    ContextInfo last = history.get(history.size() - 1);
    System.out.println(last.date);
    contextManager.pack(3, 1);
    history = contextManager.getContextHistory();
    assertEquals(3, history.size());
    System.out.println(history.get(0).date);
    assertEquals("/context2", history.get(0).name);
  }

  private WorkingContextManager getContextManager() {
    return WorkingContextManager.getInstance(getProject());
  }
}
