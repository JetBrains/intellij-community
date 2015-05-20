/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.tasks.actions.SwitchTaskCombo;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 *         Date: 3/23/12
 */
public class TaskUiTest extends CodeInsightFixtureTestCase {

  public void testTaskComboVisible() throws Exception {

    SwitchTaskCombo combo = null;
    ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_TOOLBAR);
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group, true);
    AnAction[] children = group.getChildren(new TestActionEvent());
    for (AnAction child : children) {
      if (child instanceof ActionGroup) {
        AnAction[] actions = ((ActionGroup)child).getChildren(new TestActionEvent());
        for (AnAction action : actions) {
          if (action instanceof SwitchTaskCombo) {
            combo = (SwitchTaskCombo)action;
          }
        }
      }
    }

    TaskManager manager = TaskManager.getManager(getProject());
    LocalTask defaultTask = manager.getActiveTask();
    assertTrue(defaultTask.isDefault());
    assertEquals(defaultTask.getCreated(), defaultTask.getUpdated());

    Presentation presentation = doTest(combo, toolbar);
    assertFalse(presentation.isVisible());

    try {
      TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO = true;
      presentation = doTest(combo, toolbar);
      assertTrue(presentation.isVisible());
    }
    finally {
      TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO = false;
    }

    LocalTask task = manager.createLocalTask("test");
    manager.activateTask(task, false);

    presentation = doTest(combo, toolbar);
    assertTrue(presentation.isVisible());

    manager.activateTask(defaultTask, false);
    task = manager.getActiveTask();
    assertTrue(task.isDefault());

    presentation = doTest(combo, toolbar);
    if (!presentation.isVisible()) {
      LocalTask activeTask = manager.getActiveTask();
      System.out.println(activeTask);
      System.out.println(activeTask.getCreated());
      System.out.println(activeTask.getUpdated());
      fail();
    }
  }

  private static Presentation doTest(AnAction action, ActionToolbarImpl toolbar) {
    TestActionEvent event = new TestActionEvent(toolbar.getPresentation(action));
    action.update(event);
    return event.getPresentation();
  }

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public TaskUiTest() {
    IdeaTestCase.initPlatformPrefix();
  }
}
