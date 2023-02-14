// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.security.TokenReader;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class LocalMavenIndexServerRemoteProcessSupport extends MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport {

  private final Sdk myJdk;
  private final String myOptions;
  private final MavenDistribution myDistribution;
  private final Integer myDebugPort;

  public LocalMavenIndexServerRemoteProcessSupport(@NotNull Sdk jdk,
                                                   @Nullable String vmOptions,
                                                   @NotNull MavenDistribution mavenDistribution,
                                                   @Nullable Integer debugPort) {
    super(MavenServer.class);

    myJdk = jdk;
    myOptions = vmOptions;
    myDistribution = mavenDistribution;
    myDebugPort = debugPort;
  }

  @Override
  protected void fireModificationCountChanged() {

  }

  @Override
  protected String getName(@NotNull Object o) {
    return LocalMavenIndexServerRemoteProcessSupport.class.getSimpleName();
  }

  @Override
  protected RunProfileState getRunProfileState(@NotNull Object target, @NotNull Object configuration, @NotNull Executor executor) {
    return new MavenIndexerCMDState(myJdk, myOptions, myDistribution, myDebugPort);
  }

  @Override
  public String type() {
    return "Local";
  }

  @Override
  public void onTerminate(Consumer<ProcessEvent> onTerminate) {

  }

  @Override
  protected void sendDataAfterStart(ProcessHandler handler) {
    if (handler.getProcessInput() == null) {
      return;
    }
    try (OutputStreamWriter writer = new OutputStreamWriter(handler.getProcessInput(), StandardCharsets.UTF_8)) {
      writer.write(TokenReader.PREFIX + MavenRemoteObjectWrapper.ourToken);
      writer.write(System.lineSeparator());
      writer.flush();
      MavenLog.LOG.info("Sent token to maven server");
    }
    catch (IOException e) {
      MavenLog.LOG.warn("Cannot send token to maven server", e);
    }
  }
}

