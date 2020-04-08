// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;

public interface PyCodeExecutor {
  void executeCode(@Nullable String code, @Nullable Editor e);
}
