// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.util.SmartList;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Objects;

public interface MavenLogEntryReader {

  void pushBack();

  @Nullable
  MavenLogEntry readLine();

  default List<MavenLogEntry> readUntil(Predicate<MavenLogEntry> logEntryPredicate) {
    List<MavenLogEntry> result = new SmartList<>();
    MavenLogEntry next;
    while ((next = readLine()) != null) {
      if (logEntryPredicate.apply(next)) {
        result.add(next);
      }
      else {
        pushBack();
        break;
      }
    }
    return result;
  }

  class MavenLogEntry {
    @Nullable final LogMessageType myType;
    @NotNull final String myLine;

    @TestOnly
    MavenLogEntry(@NotNull String line, LogMessageType type) {
      myLine = line;
      myType = type;
    }

    MavenLogEntry(@NotNull String line) {
      line = clearProgressCarriageReturns(line);
      myType = LogMessageType.determine(line);
      myLine = clearLine(myType, line);
    }

    @NotNull
    private static String clearProgressCarriageReturns(@NotNull String line) {
      int i = line.lastIndexOf("\r");
      if (i == -1) return line;
      return line.substring(i + 1);
    }

    @NotNull
    private static String clearLine(@Nullable LogMessageType type, @NotNull String line) {
      return type == null ? line : type.clearLine(line);
    }

    @Nullable
    public LogMessageType getType() {
      return myType;
    }

    @NotNull
    public String getLine() {
      return myLine;
    }


    @Override
    public String toString() {
      return myType == null ? myLine : "[" + myType.toString() + "] " + myLine;
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
