// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;

public class MavenToolWindowUpdateListener implements MavenImportStatusListener {
  @Override
  public void importFinished(@NotNull MavenImportedContext context) {
    MavenProjectsNavigator.getInstance(context.getProject()).scheduleStructureUpdate();
  }
}
