// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.Disposable;
import com.intellij.ui.PanelWithAnchor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.function.Consumer;


public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, PanelWithAnchor {
  @NonNls String EXPAND_PROPERTY_KEY = "ExpandEnvironmentPanel";

  JComponent getMainPanel();

  /**
   * Subscribes to live SDK-table changes to keep the interpreter list up to date. The subscription is released when
   * {@code parentDisposable} is disposed.
   */
  void subscribe(@NotNull Disposable parentDisposable);

  void addInterpreterModeListener(Consumer<Boolean> listener);
}
