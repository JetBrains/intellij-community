// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenSpyOutputParser;

import java.util.function.BiConsumer;

public class MavenSimpleConsoleEventsBuffer {
  private final TypedBuffer myBuffer;
  private final BiConsumer<String, Key<String>> myConsumer;
  private final boolean myShowSpyOutput;

  public MavenSimpleConsoleEventsBuffer(BiConsumer<String, Key<String>> consumer, boolean showSpyOutput) {
    myConsumer = consumer;
    myShowSpyOutput = showSpyOutput;
    myBuffer = new TypedBuffer(consumer);
  }

  public void addText(@NotNull String text, @NotNull Key<String> outputType) {
    if (myShowSpyOutput) {
      myConsumer.accept(text, outputType);
      return;
    }

    myBuffer.append(text, outputType);
  }

  private static final class TypedBuffer {
    private final BiConsumer<String, @NotNull Key<String>> myConsumer;
    private final StringBuilder myBuilder = new StringBuilder();
    private Key<String> myOutputType;
    private boolean myIsProcessingSpyNow;

    private TypedBuffer(BiConsumer<String, @NotNull Key<String>> consumer)
    {
      this.myConsumer = consumer;
    }

    private void reset() {
      myBuilder.setLength(0);
      myOutputType = null;
    }

    private void sendAndReset() {
      myConsumer.accept(getText(), myOutputType);
      reset();
    }

    private String getText() {
      return myBuilder.toString();
    }

    private boolean canAppend(@NotNull Key<String> outputType) {
      return null == myOutputType || myOutputType.toString().equals(outputType.toString());
    }

    public void append(@NotNull String text, @NotNull Key<String> outputType) {
      boolean lastChunk = text.charAt(text.length() - 1) == '\n';
      if (myIsProcessingSpyNow) {
        myIsProcessingSpyNow = !lastChunk;
        return;
      }
      if (!canAppend(outputType)){
        sendAndReset();
      }
      myBuilder.append(text);
      myOutputType = outputType;
      if (myBuilder.length() >= MavenSpyOutputParser.PREFIX.length() || lastChunk) {
        if (!MavenSpyOutputParser.isSpyLog(getText())) {
          sendAndReset();
        } else {
          myIsProcessingSpyNow = !lastChunk;
          reset();
        }
      }
    }

  }
}
