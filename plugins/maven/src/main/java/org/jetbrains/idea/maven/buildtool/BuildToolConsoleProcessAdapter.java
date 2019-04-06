// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class BuildToolConsoleProcessAdapter extends ProcessAdapter {
  private final MavenBuildEventProcessor myEventParser;
  private final AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();

  public BuildToolConsoleProcessAdapter(MavenBuildEventProcessor eventParser) {myEventParser = eventParser;}

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
    myEventParser.start(null, null);
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    myDecoder.escapeText(event.getText(), outputType, myEventParser);
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myEventParser.finish();
  }
}
