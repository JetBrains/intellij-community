// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
public final class Bootstrap {
  /**
   * This property allows applying patches without creating backups. In this case a callee is responsible for that.
   * Example: JetBrains Toolbox App copies a tool it wants to update and then applies the patch.
   */
  private static final String NO_BACKUP_PROPERTY = "no.backup";

  public static void main(String[] args) {
    try {
      mainNoExceptionsCatch(args);
    }
    catch (Throwable t) {
      //noinspection CallToPrintStackTrace
      t.printStackTrace();
      System.exit(1);
    }
  }

  private static void mainNoExceptionsCatch(String[] args) throws Exception {
    if (args.length != 1) throw new Exception("Expected one argument: path to application installation");

    Path target = Paths.get(args[0]);
    if (isMac() && target.getFileName().toString().endsWith(".app")) {
      Path inner = target.resolve("Contents");
      if (Files.isDirectory(inner)) {
        target = inner;
      }
    }
    if (!Files.isDirectory(target)) throw new Exception("Not a directory: " + target);

    List<String> runnerArgs = new ArrayList<>();
    runnerArgs.add("apply");
    runnerArgs.add(args[0]);
    runnerArgs.add("--toolbox-ui");
    if (Boolean.getBoolean(NO_BACKUP_PROPERTY)) {
      runnerArgs.add("--no-backup");
    }

    //noinspection SSBasedInspection
    Runner.main(runnerArgs.toArray(new String[0]));
  }

  private static boolean isMac() {
    return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac");
  }
}
