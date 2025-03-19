// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.List;

public class MergeSourceHierarchyBuilder implements ThrowableConsumer<Pair<LogEntry, Integer>, SvnBindException> {

  private LogHierarchyNode myCurrentHierarchy;
  private final @NotNull Consumer<? super LogHierarchyNode> myConsumer;

  public MergeSourceHierarchyBuilder(@NotNull Consumer<? super LogHierarchyNode> consumer) {
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
