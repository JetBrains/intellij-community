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

  public MavenSpyEventsBuffer(BiConsumer<String, Key> consumer) { myConsumer = consumer; }

  public void addText(@NotNull String text, @NotNull Key outputType) {
    int index = text.indexOf('\n');
    while (index != -1) {
      myConsumer.accept(myBuffer.append(text, 0, index).append("\n").toString(), outputType);
      myBuffer.setLength(0);
      text = text.substring(index + 1);
      index = text.indexOf('\n');
    }
    if (!text.isEmpty()) {
      myBuffer.append(text);
    }
  }
}
