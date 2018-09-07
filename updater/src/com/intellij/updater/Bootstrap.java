// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.*;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
public class Bootstrap {
  private static final String IJ_PLATFORM_UPDATER = "ijPlatformUpdater";

  /**
   * This property allows applying patches without creating backups. In this case a callee is responsible for that.
   * Example: JetBrains Toolbox App copies a tool it wants to update and then applies the patch.
   */
  private static final String NO_BACKUP_PROPERTY = "no.backup";

  public static void main(String[] args) throws Exception {
    if (args.length != 1) return;

    String path = args[0].endsWith("\\") || args[0].endsWith("/") ? args[0] : args[0] + File.separator;
    if (isMac() && path.endsWith(".app/")) {
      File file = new File(path + "Contents");
      if (file.exists() && file.isDirectory()) {
        path += "Contents/";
      }
    }

    cleanUp();

    ClassLoader cl = Bootstrap.class.getClassLoader();
    URL dependencies = cl.getResource("dependencies.txt");
    if (dependencies == null) {
      log("missing dependencies file");
      return;
    }

    List<URL> urls = new ArrayList<>();
    List<File> files = new ArrayList<>();

    urls.add(((JarURLConnection)dependencies.openConnection()).getJarFileURL());

    try (BufferedReader br = new BufferedReader(new InputStreamReader(dependencies.openStream()))) {
      String line;
      while ((line = br.readLine()) != null) {
        File file = new File(path + line);
        Path tmp = Files.createTempFile(IJ_PLATFORM_UPDATER + file.getName(), "");
        try (OutputStream targetStream = Files.newOutputStream(tmp)) {
          Files.copy(file.toPath(), targetStream);
        }
        urls.add(tmp.toFile().toURI().toURL());
        files.add(tmp.toFile());
      }
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log(System.getProperty("os.name"));
      try {
        for (File file : files) {
          log("Deleting " + file.getName() + " - " + (file.delete() ? "OK" : "FAIL"));
        }
      }
      catch (Exception e) {
        log(e);
      }
    }));

    try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), null)) {
      Class<?> runner = loader.loadClass("com.intellij.updater.Runner");
      Method main = runner.getMethod("main", String[].class);

      List<String> runnerArgs = new ArrayList<>();
      runnerArgs.add("apply");
      runnerArgs.add(args[0]);
      runnerArgs.add("--toolbox-ui");
      if (Boolean.getBoolean(NO_BACKUP_PROPERTY)) {
        runnerArgs.add("--no-backup");
      }

      //noinspection SSBasedInspection
      main.invoke(null, (Object)runnerArgs.toArray(new String[0]));
    }
  }

  private static void cleanUp() {
    log("Cleaning up...");
    try {
      Path file = Files.createTempFile("", "");
      try (Stream<Path> listing = Files.list(file.getParent())) {
        listing.forEach((p) -> {
          if (!p.toFile().isDirectory() && p.toFile().getName().startsWith(IJ_PLATFORM_UPDATER)) {
            try {
              log("Deleting " + p.toString());
              Files.delete(p);
            }
            catch (IOException e) {
              log("Can't delete " + p.toString());
              log(e);
            }
          }
        });
      }
      Files.delete(file);
    }
    catch (IOException e) {
      log(e);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(String msg) {
    System.out.println(msg);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(Throwable ex) {
    ex.printStackTrace(System.err);
  }

  private static boolean isMac() {
    return System.getProperty("os.name").toLowerCase(Locale.US).startsWith("mac");
  }
}