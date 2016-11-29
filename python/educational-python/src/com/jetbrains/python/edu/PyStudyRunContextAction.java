/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.jetbrains.python.edu;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.EditorGutter;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

public class PyStudyRunContextAction extends RunContextAction {

  public PyStudyRunContextAction(@NotNull final Executor executor) {
    super(executor);
  }

  @Override
  public void update(AnActionEvent event) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(event.getDataContext());
    final Location location = context.getLocation();
    if (location == null) return;
    super.update(event);
    final InputEvent inputEvent = event.getInputEvent();
    final Presentation presentation = event.getPresentation();

    if (inputEvent == null && !(context.getDataContext().getData(PlatformDataKeys.CONTEXT_COMPONENT) instanceof EditorGutter)) {
      presentation.setText("");
    }
  }

}
