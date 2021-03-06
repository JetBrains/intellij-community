// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.fileType;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class ShShebangFileTypeDetectorActivity implements StartupActivity.Background {
  @Override
  public void runActivity(@NotNull Project project) {
    ShShebangFileTypeDetector.getInstance(project).subscribe();
  }
}
