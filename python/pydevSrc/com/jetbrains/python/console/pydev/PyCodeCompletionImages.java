// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.console.pydev;

import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class PyCodeCompletionImages {
  /**
   * Returns an image for the given type
   */
  public static @Nullable Icon getImageForType(int type) {
    return switch (type) {
      case IToken.TYPE_CLASS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Class);
      case IToken.TYPE_FUNCTION -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Method);
      default -> null;
    };
  }
}
