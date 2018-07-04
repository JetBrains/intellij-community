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
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.List;

/**
* @author Konstantin Kolosovsky.
*/
public class MergeSourceHierarchyBuilder implements ThrowableConsumer<Pair<LogEntry, Integer>, SvnBindException> {

  private LogHierarchyNode myCurrentHierarchy;
  @NotNull private final Consumer<LogHierarchyNode> myConsumer;

  public MergeSourceHierarchyBuilder(@NotNull Consumer<LogHierarchyNode> consumer) {
    myConsumer = consumer;
  }

  @Override
  public void consume(Pair<LogEntry, Integer> svnLogEntryIntegerPair) {
    final LogEntry logEntry = svnLogEntryIntegerPair.getFirst();
    final Integer mergeLevel = svnLogEntryIntegerPair.getSecond();

    if (mergeLevel < 0) {
      if (myCurrentHierarchy != null) {
        myConsumer.consume(myCurrentHierarchy);
      }
      if (logEntry.hasChildren()) {
        myCurrentHierarchy = new LogHierarchyNode(logEntry);
      } else {
        // just pass
        myCurrentHierarchy = null;
        myConsumer.consume(new LogHierarchyNode(logEntry));
      }
    } else {
      addToLevel(myCurrentHierarchy, logEntry, mergeLevel);
    }
  }

  public void finish() {
    if (myCurrentHierarchy != null) {
      myConsumer.consume(myCurrentHierarchy);
    }
  }

  private static void addToLevel(final LogHierarchyNode tree, final LogEntry entry, final int left) {
    assert tree != null;
    if (left == 0) {
      tree.add(entry);
    } else {
      final List<LogHierarchyNode> children = tree.getChildren();
      assert ! children.isEmpty();
      addToLevel(children.get(children.size() - 1), entry, left - 1);
    }
  }
}
