// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenImportEventProcessor;
import org.jetbrains.idea.maven.execution.MavenSpyEventsBuffer;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.security.TokenReader;
import org.jetbrains.idea.maven.server.ssl.SslDelegateHandlerConfirmingTrustManager;
import org.jetbrains.idea.maven.server.ssl.SslDelegateHandlerStateMachine;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public abstract class AbstractMavenServerRemoteProcessSupport extends MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport {
  protected final Sdk myJdk;
  protected final String myOptions;
  protected final MavenDistribution myDistribution;
  protected final Project myProject;
  protected final Integer myDebugPort;
  protected @Nullable Consumer<ProcessEvent> onTerminate;
  private final MavenImportEventProcessor myImportEventProcessor;
  private final MavenSpyEventsBuffer myMavenSpyEventsBuffer;
  private final SslDelegateHandlerStateMachine mySslDelegateHandlerStateMachine;

  public AbstractMavenServerRemoteProcessSupport(@NotNull Sdk jdk,
                                                 @Nullable String vmOptions,
                                                 @NotNull MavenDistribution mavenDistribution,
                                                 @NotNull Project project,
                                                 @Nullable Integer debugPort) {
    super(MavenServer.class);
    myJdk = jdk;
    myOptions = vmOptions;
    myDistribution = mavenDistribution;
    myProject = project;
    myDebugPort = debugPort;

    myImportEventProcessor = new MavenImportEventProcessor(project);
    AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();
    mySslDelegateHandlerStateMachine = new SslDelegateHandlerConfirmingTrustManager(project);

    myMavenSpyEventsBuffer = new MavenSpyEventsBuffer((l, k) -> {
      mySslDelegateHandlerStateMachine.addLine(l);
      myDecoder.escapeText(l, k, myImportEventProcessor);
    });
  }


  @Override
  protected void fireModificationCountChanged() {
  }

  @Override
  protected String getName(@NotNull Object file) {
    return MavenServerManager.class.getSimpleName();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Override
  protected void sendDataAfterStart(ProcessHandler handler) {
    if (handler.getProcessInput() == null) {
      return;
    }
    mySslDelegateHandlerStateMachine.setOutput(handler.getProcessInput());
    OutputStreamWriter writer = new OutputStreamWriter(handler.getProcessInput(), StandardCharsets.UTF_8);
    try {
      writer.write(TokenReader.PREFIX + MavenRemoteObjectWrapper.ourToken);
      writer.write(System.lineSeparator());
      writer.flush();
      MavenLog.LOG.info("Sent token to maven server");
    }
    catch (IOException e) {
      MavenLog.LOG.warn("Cannot send token to maven server", e);
    }
  }

  @Override
  public void onTerminate(Consumer<ProcessEvent> onTerminate) {
    this.onTerminate = onTerminate;
  }

  @Override
  protected void onProcessTerminated(ProcessEvent event) {
    Consumer<ProcessEvent> eventConsumer = onTerminate;

    if (eventConsumer != null) {
      eventConsumer.accept(event);
    }

    if (event.getExitCode() == 0) {
      return;
    }


    myImportEventProcessor.finish();

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

  @Override
  protected void logText(@NotNull Object configuration,
                         @NotNull ProcessEvent event,
                         @NotNull Key outputType) {
    super.logText(configuration, event, outputType);
    myMavenSpyEventsBuffer.addText(event.getText(), outputType);
  }
}