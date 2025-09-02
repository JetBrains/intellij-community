// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.maven.server;

import java.util.function.Consumer;

import static org.jetbrains.maven.server.SpyConstants.*;

public final class EventInfoPrinter {
  private final Consumer<String> myPrinter;

  public EventInfoPrinter(Consumer<String> printer) {
    myPrinter = printer;
  }

  private void print(Object type, CharSequence... args) {
    myPrinter.accept(printToBuffer(type, args).toString());
  }

  public static StringBuilder printToBuffer(Object type, CharSequence... args) {
    StringBuilder out = new StringBuilder();
    out.append(PREFIX);
    out.append(Thread.currentThread().getId());
    out.append('-');
    out.append(type);

    if (args != null) {
      for (int i = 0; i < args.length; i += 2) {
        out.append(SEPARATOR);
        out.append(args[i]);
        out.append('=');
        appendReplacingNewLines(out, args[i + 1]);
      }
    }
    return out;
  }

  private static void appendReplacingNewLines(StringBuilder out, CharSequence value) {
    if (value == null) {
      out.append("null");
      return;
    }

    int appendFrom = 0;
    int length = value.length();
    for (int i = 0; i < length; i++) {
      char ch = value.charAt(i);
      if (ch == '\n') {
        out.append(value.subSequence(appendFrom, i));

        if (i < length - 1 && value.charAt(i + 1) == '\r') {
          //noinspection AssignmentToForLoopParameter
          i++;
        }
        out.append(NEWLINE);
        appendFrom = i + 1;
      }
    }
    if (appendFrom == 0) {
      out.append(value);
    }
    else {
      out.append(value.subSequence(appendFrom, value.length()));
    }
  }

  public void printMavenEventInfo(Object type, String name1, CharSequence value1) {
    print(type, name1, value1);
  }

  public void printMavenEventInfo(Object type, String name1, CharSequence value1, String name2, CharSequence value2) {
    print(type, name1, value1, name2, value2);
  }

  public void printMavenEventInfo(Object type,
                                         String name1,
                                         CharSequence value1,
                                         String name2,
                                         CharSequence value2,
                                         String name3,
                                         CharSequence value3) {
    print(type, name1, value1, name2, value2, name3, value3);
  }

  public void printMavenEventInfo(Object type,
                                         String name1,
                                         CharSequence value1,
                                         String name2,
                                         CharSequence value2,
                                         String name3,
                                         CharSequence value3,
                                         String name4,
                                         CharSequence value4) {
    print(type, name1, value1, name2, value2, name3, value3, name4, value4);
  }
}
