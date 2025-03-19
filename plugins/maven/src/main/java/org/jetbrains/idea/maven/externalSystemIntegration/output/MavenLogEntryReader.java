// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public interface MavenLogEntryReader {
  void pushBack();

  @Nullable
  MavenLogEntry readLine();

  /**
   * Read lines while predicate is true
   *
   */
  default List<MavenLogEntry> readWhile(Predicate<MavenLogEntry> logEntryPredicate) {
    List<MavenLogEntry> result = new SmartList<>();
    MavenLogEntry next;
    while ((next = readLine()) != null) {
      if (logEntryPredicate.test(next)) {
        result.add(next);
      }
      else {
        pushBack();
        break;
      }
    }
    return result;
  }

  /**
   * read first line which matches the predicate, other lines are ignored
   *
   */
  default MavenLogEntry findFirst(Predicate<MavenLogEntry> logEntryPredicate) {
    MavenLogEntry result;
    MavenLogEntry next;
    while ((result = readLine()) != null) {
      if (logEntryPredicate.test(result)) {
        return result;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  final class MavenLogEntry {
    public final @Nullable LogMessageType myType;
    public final @NotNull String myLine;

    @TestOnly
    public MavenLogEntry(@NotNull String line, LogMessageType type) {
      myLine = line;
      myType = type;
    }

    public MavenLogEntry(@NotNull String line) {
      line = clearProgressCarriageReturns(line);
      myType = LogMessageType.determine(line);
      myLine = clearLine(myType, line);
    }

    private static @NotNull String clearProgressCarriageReturns(@NotNull String line) {
      int i = line.lastIndexOf("\r");
      if (i == -1) return line;
      return line.substring(i + 1);
    }

    private static @NotNull String clearLine(@Nullable LogMessageType type, @NotNull String line) {
      return type == null ? line : type.clearLine(line);
    }

    public @Nullable LogMessageType getType() {
      return myType;
    }

    public @NotNull @NlsSafe String getLine() {
      return myLine;
    }

    @Override
    public String toString() {
      return myType == null ? myLine : "[" + myType + "] " + myLine;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MavenLogEntry entry = (MavenLogEntry)o;
      return myType == entry.myType &&
             myLine.equals(entry.myLine);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myType, myLine);
    }
  }
}
