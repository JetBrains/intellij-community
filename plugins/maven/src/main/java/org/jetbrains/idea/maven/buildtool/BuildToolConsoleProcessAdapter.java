// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenSpyEventsBuffer;

@ApiStatus.Experimental
public class BuildToolConsoleProcessAdapter extends ProcessAdapter {
  private final MavenBuildEventProcessor myEventParser;
  private final boolean myProcessText;
  private final AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();
  private final MavenSpyEventsBuffer myMavenSpyEventsBuffer;


  /**
   * @param eventParser
   * @param processText to be removed after IDEA-216278
   */
  public BuildToolConsoleProcessAdapter(MavenBuildEventProcessor eventParser, @Deprecated boolean processText) {
    myEventParser = eventParser;
    myProcessText = processText;
    if (processText) {
      myMavenSpyEventsBuffer = new MavenSpyEventsBuffer((l, k) -> myDecoder.escapeText(l, k, myEventParser));
    }
    else {
      myMavenSpyEventsBuffer = null;
    }
  }

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
    myEventParser.start();
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (myProcessText) {
      myMavenSpyEventsBuffer.addText(event.getText(), outputType);
    }
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myEventParser.finish();
  }
}
