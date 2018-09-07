// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public class SvnMergeSourceTracker implements ThrowableConsumer<LogEntry, SvnBindException> {
  private int myMergeLevel;
  // -1 - not merge source; 0 - direct merge source
  private final ThrowableConsumer<Pair<LogEntry, Integer>, SvnBindException> myConsumer;

  public SvnMergeSourceTracker(@NotNull ThrowableConsumer<Pair<LogEntry, Integer>, SvnBindException> consumer) {
    myConsumer = consumer;
    myMergeLevel = -1;
  }

  @Override
  public void consume(@NotNull LogEntry logEntry) throws SvnBindException {
    if (logEntry.getRevision() < 0) {
      -- myMergeLevel;
      return;
    }
    myConsumer.consume(new Pair<>(logEntry, myMergeLevel));
    if (logEntry.hasChildren()) {
      ++ myMergeLevel;
    }
  }
}
