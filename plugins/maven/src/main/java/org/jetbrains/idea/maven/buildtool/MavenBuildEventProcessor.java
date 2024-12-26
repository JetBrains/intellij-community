// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogOutputParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenOutputParserProvider;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;

import java.util.Collections;
import java.util.function.Function;

@ApiStatus.Experimental
public class MavenBuildEventProcessor implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private final @NotNull BuildProgressListener myBuildProgressListener;
  private final @NotNull BuildOutputInstantReaderImpl myInstantReader;
  private final @NotNull MavenLogOutputParser myParser;
  private boolean closed = false;
  private final BuildDescriptor myDescriptor;
  private final @NotNull Function<MavenParsingContext, StartBuildEvent> myStartBuildEventSupplier;

  public MavenBuildEventProcessor(@NotNull MavenRunConfiguration runConfiguration,
                                  @NotNull BuildProgressListener buildProgressListener,
                                  @NotNull BuildDescriptor descriptor,
                                  @NotNull ExternalSystemTaskId taskId,
                                  @NotNull Function<String, String> targetFileMapper,
                                  @Nullable Function<MavenParsingContext, StartBuildEvent> startBuildEventSupplier) {

    myBuildProgressListener = buildProgressListener;
    myDescriptor = descriptor;
    myStartBuildEventSupplier = startBuildEventSupplier != null
                                ? startBuildEventSupplier : ctx -> new StartBuildEventImpl(myDescriptor, "");

    myParser = MavenOutputParserProvider.createMavenOutputParser(runConfiguration, taskId, targetFileMapper);

    myInstantReader = new BuildOutputInstantReaderImpl(
      taskId, taskId,
      myBuildProgressListener,
      Collections.singletonList(myParser));
  }

  public synchronized void finish() {
    myParser.finish(e -> myBuildProgressListener.onEvent(myDescriptor.getId(), e));
    myInstantReader.close();
    closed = true;
  }

  public void start() {
    StartBuildEvent startEvent = myStartBuildEventSupplier.apply(getParsingContext());

    myBuildProgressListener.onEvent(myDescriptor.getId(), startEvent);
  }

  public synchronized void onTextAvailable(String text, boolean stdError) {
    if (!closed) {
      myInstantReader.append(text);
    }
  }

  public MavenParsingContext getParsingContext() {
    return myParser.getParsingContext();
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    onTextAvailable(text, ProcessOutputType.isStderr(outputType));
  }
}
