// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.MessageEvent;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;

public class WarningNotifier extends MessageNotifier {

  public WarningNotifier() {
    super(LogMessageType.WARNING, MessageEvent.Kind.WARNING, RunnerBundle.message("build.event.title.warning"));
  }
}
