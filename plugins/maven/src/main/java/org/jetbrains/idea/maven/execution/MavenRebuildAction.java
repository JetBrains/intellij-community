// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenRebuildAction extends FakeRerunAction {

  private final ExecutionEnvironment myEnvironment;

  public MavenRebuildAction(ExecutionEnvironment environment) {
    myEnvironment = environment;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    presentation.setText(ExecutionBundle.messagePointer("rerun.configuration.action.name",
                                                 StringUtil.escapeMnemonics(myEnvironment.getRunProfile().getName())));
    presentation.setIcon(
      ExecutionManagerImpl.isProcessRunning(myEnvironment.getContentToReuse()) ?
      AllIcons.Actions.Restart : AllIcons.Actions.Compile);
  }

  @Override
  protected @Nullable RunContentDescriptor getDescriptor(AnActionEvent event) {
    return myEnvironment.getContentToReuse();
  }

  @Override
  protected @Nullable ExecutionEnvironment getEnvironment(@NotNull AnActionEvent event) {
    return myEnvironment;
  }
}
