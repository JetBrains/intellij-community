// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class PyLocalAttachGroup implements XLocalAttachGroup {
  public static final PyLocalAttachGroup INSTANCE = new PyLocalAttachGroup();

  private PyLocalAttachGroup() {
  }

  @Override
  public int getOrder() {
    return XLocalAttachGroup.DEFAULT.getOrder() - 10;
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Python";
  }

  @NotNull
  @Override
  public Icon getProcessIcon(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
    return AllIcons.RunConfigurations.Application;
  }

  @NotNull
  @Override
  public String getProcessDisplayText(@NotNull Project project, @NotNull ProcessInfo info, @NotNull UserDataHolder dataHolder) {
    return info.getArgs();
  }
}
