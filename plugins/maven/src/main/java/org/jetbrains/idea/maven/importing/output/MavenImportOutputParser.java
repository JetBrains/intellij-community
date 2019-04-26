// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class MavenImportOutputParser implements BuildOutputParser {
  @Override
  public boolean parse(@NotNull String line,
                       @NotNull BuildOutputInstantReader reader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer) {
    return false;
  }
}
