// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.util.NlsContexts.DialogMessage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ValidationListener {
  void onError(@DialogMessage @NotNull String text, @NotNull JComponent component, boolean forbidSave);

  void onSuccess();
}
