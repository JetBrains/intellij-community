// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.Maven3SpyOutputExtractor;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.Maven4SpyOutputExtractor;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.SpyOutputExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;


public class MavenSimpleConsoleEventsBuffer {
  private final TypedBuffer myBuffer;
  private final BiConsumer<String, Key<Object>> myConsumer;
  private final boolean myShowSpyOutput;
  private final boolean myHideWindowsExitMessage;

  public static class Builder {
    private final BiConsumer<String, Key<Object>> consumer;
    private boolean showSpyOutput;
    private boolean loggingOutputStream;
    private boolean hideWindowsCmdMessage;

    public Builder(BiConsumer<String, Key<Object>> consumer) {
      this.consumer = consumer;
    }

    public Builder withSpyOutput(boolean v) {
      showSpyOutput = v;
      return this;
    }

    public Builder withLoggingOutputStream(boolean v) {
      loggingOutputStream = v;
      return this;
    }

    public Builder withHidingCmdExitQuestion(boolean v) {
      hideWindowsCmdMessage = v;
      return this;
    }

    public MavenSimpleConsoleEventsBuffer build() {
      return new MavenSimpleConsoleEventsBuffer(consumer, showSpyOutput, loggingOutputStream, hideWindowsCmdMessage);
    }
  }

  private MavenSimpleConsoleEventsBuffer(BiConsumer<String, Key<Object>> consumer,
                                         boolean showSpyOutput,
                                         boolean withLoggingOutputStream,
                                         boolean hideWindowsMessage) {
    myConsumer = consumer;
    myShowSpyOutput = showSpyOutput;
    myHideWindowsExitMessage = hideWindowsMessage;
    myBuffer = new TypedBuffer(consumer, withLoggingOutputStream ? new Maven4SpyOutputExtractor() : new Maven3SpyOutputExtractor());
  }

  public void addText(@NotNull String text, @NotNull Key<Object> outputType) {
    if (myShowSpyOutput) {
      myConsumer.accept(text, outputType);
      return;
    }

    myBuffer.append(text, outputType);
  }

  private record OutputChunk(String text, Key<Object> outputType) {
  }

  private static final class TypedBuffer {
    private final BiConsumer<String, @NotNull Key<Object>> myConsumer;
    private final SpyOutputExtractor myExtractor;
    private final List<OutputChunk> chunks = new ArrayList<>();
    private ProcessOutputType myBaseOutputType;
    private boolean myIsProcessingSpyNow;

    private TypedBuffer(BiConsumer<String, @NotNull Key<Object>> consumer, SpyOutputExtractor extractor) {
      this.myConsumer = consumer;
      myExtractor = extractor;
    }

    private void reset() {
      chunks.clear();
    }

    private void sendAndReset() {
      for (OutputChunk chunk : chunks) {
        myConsumer.accept(chunk.text, chunk.outputType);
      }
      reset();
    }

    private boolean canAppend(@NotNull Key<Object> outputType) {
      ProcessOutputType baseOutType = getBaseOutputType(outputType);
      return null == myBaseOutputType || (baseOutType != null && baseOutType.equals(myBaseOutputType));
    }

    private static @Nullable ProcessOutputType getBaseOutputType(Key<Object> outputType) {
      if (!(outputType instanceof ProcessOutputType)) return null;
      return ((ProcessOutputType)outputType).getBaseOutputType();
    }

    public void append(@NotNull String text, @NotNull Key<Object> outputType) {
      boolean lastChunk = text.charAt(text.length() - 1) == '\n';
      if (myIsProcessingSpyNow) {
        myIsProcessingSpyNow = !lastChunk;
        return;
      }
      if (!canAppend(outputType)) {
        sendAndReset();
      }
      chunks.add(new OutputChunk(text, outputType));
      myBaseOutputType = getBaseOutputType(outputType);
      String clearedBuffer = getAllInBuffer();
      if (myExtractor.isLengthEnough(clearedBuffer) || lastChunk) {
        if (!myExtractor.isSpyLog(clearedBuffer)) {
          sendAndReset();
        }
        else {
          myIsProcessingSpyNow = !lastChunk;
          reset();
        }
      }
    }

    private String getAllInBuffer() {
      StringBuilder plainText = new StringBuilder();
      for (OutputChunk chunk : chunks) {
        plainText.append(chunk.text);
      }
      return plainText.toString();
    }
  }
}
