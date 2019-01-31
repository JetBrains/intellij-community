// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface RestPreviewPanel extends Disposable {

  void setHtml(@NotNull String html);

  void render();

  JComponent getComponent();
}
