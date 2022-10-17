// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenSpyOutputParser;

import java.util.function.BiConsumer;

public class MavenSimpleConsoleEventsBuffer {
  private final TypedBuffer myBuffer = new TypedBuffer();
  private final BiConsumer<String, Key<String>> myConsumer;
  private final boolean myShowSpyOutput;
  private boolean isProcessingSpyNow;

  public MavenSimpleConsoleEventsBuffer(BiConsumer<String, Key<String>> consumer, boolean showSpyOutput) {
    myConsumer = consumer;
    myShowSpyOutput = showSpyOutput;
  }

  public void addText(@NotNull String text, @NotNull Key<String> outputType) {
    if (myShowSpyOutput) {
      myConsumer.accept(text, outputType);
      return;
    }

    boolean lastChunk = text.charAt(text.length() - 1) == '\n';
    if (isProcessingSpyNow) {
      isProcessingSpyNow = !lastChunk;
      return;
    }

    if (!myBuffer.canAppend(outputType)){
      myBuffer.sendAndReset(myConsumer);
    }

    myBuffer.append(text, outputType);
    if (myBuffer.length() >= MavenSpyOutputParser.PREFIX.length() || lastChunk) {
      if (!MavenSpyOutputParser.isSpyLog(myBuffer.getText())) {
        myBuffer.sendAndReset(myConsumer);
      } else {
        isProcessingSpyNow = !lastChunk;
        myBuffer.reset();
      }
    }
  }

  private static final class TypedBuffer {
    private final StringBuilder myBuilder = new StringBuilder();
    private Key<String> myOutputType;

    public void reset() {
      myBuilder.setLength(0);
      myOutputType = null;
    }

    public void sendAndReset(BiConsumer<String, @NotNull Key<String>> consumer) {
      consumer.accept(getText(), myOutputType);
      reset();
    }

    public String getText() {
      return myBuilder.toString();
    }

    public boolean canAppend(@NotNull Key<String> outputType) {
      return null == myOutputType || myOutputType.toString().equals(outputType.toString());
    }

    public void append(@NotNull String text, @NotNull Key<String> outputType) {
      if (!canAppend(outputType)) {
        throw new RuntimeException("Can't append outputType" + outputType);
      }
      myBuilder.append(text);
      myOutputType = outputType;
    }

    public int length() {
      return myBuilder.length();
    }
  }
}
