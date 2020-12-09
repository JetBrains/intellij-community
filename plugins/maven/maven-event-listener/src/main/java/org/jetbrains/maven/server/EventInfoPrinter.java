// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.maven.server;

import static org.jetbrains.maven.server.SpyConstants.*;

public final class EventInfoPrinter {


  private static void print(Object type, Object... args) {
    String format = createFormat(type, args);
    String result = PREFIX + Thread.currentThread().getId() + "-" +
                    String.format(format, args).replaceAll("\n\r?", NEWLINE);
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println(result);
  }

  private static String createFormat(Object type, Object... args) {
    int num = args == null ? 0 : args.length / 2;
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < num; i++) {
      builder.append(SEPARATOR).append("%s=%s");
    }
    builder.insert(0, type);
    return builder.toString();
  }

  public static void printMavenEventInfo(Object type, String name1, Object value1) {
    print(type, name1, value1);
  }

  public static void printMavenEventInfo(Object type, String name1, Object value1, String name2, Object value2) {
    print(type, name1, value1, name2, value2);
  }

  public static void printMavenEventInfo(Object type,
                                         String name1,
                                         Object value1,
                                         String name2,
                                         Object value2,
                                         String name3,
                                         Object value3) {
    print(type, name1, value1, name2, value2, name3, value3);
  }

  public static void printMavenEventInfo(Object type,
                                         String name1,
                                         Object value1,
                                         String name2,
                                         Object value2,
                                         String name3,
                                         Object value3,
                                         String name4,
                                         Object value4) {
    print(type, name1, value1, name2, value2, name3, value3, name4, value4);
  }
}
