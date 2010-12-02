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

package com.intellij.tasks.actions.context;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.actions.BaseTaskAction;
import com.intellij.tasks.context.WorkingContextManager;

/**
 * @author Dmitry Avdeev
 */
public class ClearContextAction extends BaseTaskAction {
  public void actionPerformed(final AnActionEvent e) {
    final Project project = getProject(e);
    GlobalUndoableAction action = new GlobalUndoableAction() {
      public void undo() throws UnexpectedUndoException {

      }

      public void redo() throws UnexpectedUndoException {
        WorkingContextManager.getInstance(project).clearContext();
      }
    };
    UndoableCommand.execute(project, action, "Clear context", "Context");
  }
}
