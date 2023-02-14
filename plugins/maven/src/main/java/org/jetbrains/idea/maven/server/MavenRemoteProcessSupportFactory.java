// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Extension point for create maven remote process.
 * And to interact with the process and handling its callbacks.
 * {@link MavenRemoteProcessSupport}
 */
@ApiStatus.Internal
public interface MavenRemoteProcessSupportFactory {
  ExtensionPointName<MavenRemoteProcessSupportFactory> MAVEN_SERVER_SUPPORT_EP_NAME = new ExtensionPointName<>("org.jetbrains.idea.maven.mavenServerSupportFactory");

  /**
   * create remote procces.
   * @param jdk - jdk data.
   * @param vmOptions - vm command line options.
   * @param distribution - maven distribution data (version, maven home).
   * @param project - idea project.
   * @param debugPort - port for remote debugging.
   * @return remote maven process.
   */
  MavenRemoteProcessSupport create(Sdk jdk,
                                   String vmOptions,
                                   MavenDistribution distribution,
                                   Project project,
                                   Integer debugPort);

  /**
   * create remote process for indexer
   * @param jdk - jdk data.
   * @param vmOptions - vm command line options.
   * @param distribution - maven distribution data (version, maven home).
   * @param debugPort - port for remote debugging.
   * @return remote maven process.
   */
  default MavenRemoteProcessSupport createIndexerSupport(Sdk jdk,
                                                         String vmOptions,
                                                         MavenDistribution distribution,
                                                         Integer debugPort) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  static MavenRemoteProcessSupportFactory forProject(@NotNull Project project) {
    MavenRemoteProcessSupportFactory applicable = MAVEN_SERVER_SUPPORT_EP_NAME.findFirstSafe(factory -> factory.isApplicable(project));
    if (applicable == null) {
      return new LocalMavenRemoteProcessSupportFactory();
    }
    return applicable;
  }

  @NotNull
  static MavenRemoteProcessSupportFactory forIndexer() {
    return new LocalMavenRemoteProcessSupportFactory();
  }

  boolean isApplicable(Project project);

  abstract class MavenRemoteProcessSupport extends RemoteProcessSupport<Object, MavenServer, Object> {
    public MavenRemoteProcessSupport(@NotNull Class<MavenServer> valueClass) {
      super(valueClass);
    }

    public abstract String type();

    public abstract void onTerminate(Consumer<ProcessEvent> onTerminate);
  }
}
