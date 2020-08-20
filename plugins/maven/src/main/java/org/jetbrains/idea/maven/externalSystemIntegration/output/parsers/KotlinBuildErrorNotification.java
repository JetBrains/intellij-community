// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import org.jetbrains.idea.maven.execution.RunnerBundle;

public class KotlinBuildErrorNotification extends BuildErrorNotification {
  public KotlinBuildErrorNotification() {
    super("kotlin", "kt", RunnerBundle.message("build.event.title.kotlin.compiler"));
  }
}
