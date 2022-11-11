// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.xdebugger.attach.XAttachProcessPresentationGroup;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class PyLocalAttachGroup implements XAttachProcessPresentationGroup {
  public static final PyLocalAttachGroup INSTANCE = new PyLocalAttachGroup();

  private PyLocalAttachGroup() {
  }

  @Override
  public int getOrder() {
    return -10;
  }

  @NotNull
  @Override
  public String getGroupName() {
    return PyBundle.message("python.local.attach.group.name");
  }

  @Override
  public @NotNull Icon getItemIcon(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
    return AllIcons.RunConfigurations.Application;
  }

  @Nls
  @Override
  public @NotNull String getItemDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
    return info.getArgs();
  }
}
