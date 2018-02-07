/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public class SvnMergeSourceTracker implements ThrowableConsumer<LogEntry, SvnBindException> {
  private int myMergeLevel;
  // -1 - not merge source; 0 - direct merge source
  private ThrowableConsumer<Pair<LogEntry, Integer>, SvnBindException> myConsumer;

  public SvnMergeSourceTracker(@NotNull ThrowableConsumer<Pair<LogEntry, Integer>, SvnBindException> consumer) {
    myConsumer = consumer;
    myMergeLevel = -1;
  }

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
