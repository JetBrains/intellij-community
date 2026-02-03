// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.actions.context;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;

/**
 * @author Dmitry Avdeev
 */
public final class UndoableCommand {

  public static void execute(final Project project, final UndoableAction action, @NlsContexts.Command String name, String groupId) {
    CommandProcessor.getInstance().executeCommand(project, () -> {

      try {
        action.redo();
      }
      catch (UnexpectedUndoException e) {
        throw new RuntimeException(e);
      }
      UndoManager.getInstance(project).undoableActionPerformed(action);

    }, name, groupId);

  }
}
