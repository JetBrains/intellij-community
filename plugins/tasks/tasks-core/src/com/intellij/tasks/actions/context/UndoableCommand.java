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

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;

/**
 * @author Dmitry Avdeev
 */
public class UndoableCommand {
  
  public static void execute(final Project project, final UndoableAction action, String name, String groupId) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {

        try {
          action.redo();
        }
        catch (UnexpectedUndoException e) {
          throw new RuntimeException(e);
        }
        UndoManager.getInstance(project).undoableActionPerformed(action);

      }
    }, name, groupId);

  }
}
