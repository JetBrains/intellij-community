// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;

public class LocalMavenRemoteProcessSupportFactory implements MavenRemoteProcessSupportFactory {
  @Override
  public MavenRemoteProcessSupport create(Sdk jdk,
                                          String vmOptions,
                                          MavenDistribution distribution,
                                          Project project,
                                          Integer debugPort) {
    MavenActionsUsagesCollector.trigger(project, MavenActionsUsagesCollector.START_LOCAL_MAVEN_SERVER);
    return new LocalMavenServerRemoteProcessSupport(jdk, vmOptions, distribution, project, debugPort);
  }

  @Override
  public MavenRemoteProcessSupport createIndexerSupport(Sdk jdk,
                                                         String vmOptions,
                                                         MavenDistribution distribution,
                                                         Integer debugPort) {
    return new LocalMavenIndexServerRemoteProcessSupport(jdk, vmOptions, distribution, debugPort);

  }

  @Override
  public boolean isApplicable(Project project) {
    return false;
  }
}
