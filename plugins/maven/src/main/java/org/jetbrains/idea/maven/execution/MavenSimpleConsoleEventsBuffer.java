// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenSpyOutputParser;

import java.util.function.BiConsumer;

public class MavenSimpleConsoleEventsBuffer {
  private final StringBuilder myBuffer = new StringBuilder();
  private final BiConsumer<String, Key> myConsumer;
  private final boolean myShowSpyOutput;
  private boolean isProcessingSpyNow;

  public MavenSimpleConsoleEventsBuffer(BiConsumer<String, Key> consumer, boolean showSpyOutput) {
    myConsumer = consumer;
    myShowSpyOutput = showSpyOutput;
  }

  public void addText(@NotNull String text, @NotNull Key outputType) {
    if (myShowSpyOutput) {
      myConsumer.accept(text, outputType);
      return;
    }

    boolean lastChunk = text.charAt(text.length() - 1) == '\n';
    if (isProcessingSpyNow) {
      myBuffer.setLength(0);

      isProcessingSpyNow = !lastChunk;
      return;
    }


    String textToSend = myBuffer.length() == 0 ? text : myBuffer + text;
    if (textToSend.length() >= MavenSpyOutputParser.PREFIX.length() || lastChunk) {
      myBuffer.setLength(0);
      if (!MavenSpyOutputParser.isSpyLog(textToSend)) {
        myConsumer.accept(textToSend, outputType);
      }
      else {
        isProcessingSpyNow = !lastChunk;
      }
    }
    else {
      myBuffer.append(text);
    }
  }
}
