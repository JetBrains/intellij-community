// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.MavenBuildEvent;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenOutputActionProcessor;

public class MavenProgressListener implements BuildProgressListener {
  private final Project myProject;
  private final BuildProgressListener myListener;
  private final MavenOutputActionProcessor myOutputActionProcessor;
  private String myWorkingDir;

  public MavenProgressListener(@NotNull Project project, @NotNull BuildProgressListener listener, @NotNull String workingDir) {
    myProject = project;
    myListener = listener;
    myWorkingDir = workingDir;
    myOutputActionProcessor = new MavenOutputActionProcessor(project, myWorkingDir);
  }


  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    if (event instanceof MavenBuildEvent) {
      ((MavenBuildEvent)event).process(myOutputActionProcessor);
    }
    else {
      myListener.onEvent(buildId, event);
    }
  }
}
