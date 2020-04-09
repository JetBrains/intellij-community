// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.security.TokenReader;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

class MavenServerRemoteProcessSupport extends RemoteProcessSupport<Object, MavenServer, Object> {
  private final MavenServerConnector myServerConnector;
  private final Project myProject;

  public MavenServerRemoteProcessSupport(MavenServerConnector mavenServerConnector,
                                         @NotNull Project project) {
    super(MavenServer.class);
    myServerConnector = mavenServerConnector;
    myProject = project;
  }


  @Override
  protected void fireModificationCountChanged() {
  }

  @Override
  protected String getName(@NotNull Object file) {
    return MavenServerManager.class.getSimpleName();
  }

  @Override
  protected RunProfileState getRunProfileState(@NotNull Object target, @NotNull Object configuration, @NotNull Executor executor) {
    return new MavenServerCMDState(myServerConnector, myProject);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Override
  protected void sendDataAfterStart(ProcessHandler handler) {
    if (handler.getProcessInput() == null) {
      return;
    }
    OutputStreamWriter writer = new OutputStreamWriter(handler.getProcessInput(), StandardCharsets.UTF_8);
    try {
      writer.write(TokenReader.PREFIX + MavenRemoteObjectWrapper.ourToken);
      writer.write(System.lineSeparator());
      writer.flush();
    }
    catch (IOException e) {
      MavenLog.LOG.warn("Cannot send token to maven server", e);
    }
  }

  @Override
  protected void onProcessTerminated(ProcessEvent event) {
    if (event.getExitCode() == 0) {
      return;
    }
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project p : openProjects) {
      ReadAction.run(() -> {
        if (p.isDisposed()) {
          return;
        }
        MavenProjectsManager manager = MavenProjectsManager.getInstance(p);
        if (!manager.isMavenizedProject()) {
          return;
        }
        manager.terminateImport(event.getExitCode());
      });
    }
  }
}
