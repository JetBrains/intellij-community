// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks.actions;

import org.jetbrains.idea.maven.tasks.MavenTasksManager;

final class ToggleAfterCompileTasksAction extends ToggleCompilerTasksAction {

  ToggleAfterCompileTasksAction() {
    super(MavenTasksManager.Phase.AFTER_COMPILE);
  }
}