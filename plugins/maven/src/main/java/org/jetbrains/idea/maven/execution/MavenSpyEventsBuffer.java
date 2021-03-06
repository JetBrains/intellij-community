// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * Expect to receive onle one line or part of it. splitToLines should be enabled
 */
public class MavenSpyEventsBuffer {
  private final StringBuilder myBuffer = new StringBuilder();
  private final BiConsumer<String, Key> myConsumer;

  public MavenSpyEventsBuffer(BiConsumer<String, Key> consumer) {myConsumer = consumer;}

  public void addText(@NotNull String text, @NotNull Key outputType) {
    if (text.charAt(text.length() - 1) == '\n') {
      String textToSend = myBuffer.length() == 0 ? text : myBuffer + text;
      myConsumer.accept(textToSend, outputType);
      myBuffer.setLength(0);
    }
    else {
      myBuffer.append(text);
    }
  }
}
