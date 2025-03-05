// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public abstract class PyCustomSdkUiProvider {
  public static final ExtensionPointName<PyCustomSdkUiProvider> EP_NAME =
    ExtensionPointName.create("Pythonid.pyCustomSdkUiProvider");

  public static @Nullable PyCustomSdkUiProvider getInstance() {
    return ContainerUtil.getFirstItem(EP_NAME.getExtensionList());
  }

  public abstract void customizeActiveSdkPanel(@NotNull Project project, @NotNull ComboBox mySdkCombo, @NotNull JPanel myMainPanel,
                                               @NotNull GridBagConstraints c, @NotNull Disposable disposable);
}
