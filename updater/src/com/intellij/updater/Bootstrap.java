/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.updater;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
public class Bootstrap {
  private static final String IJ_PLATFORM_UPDATER = "ijPlatformUpdater";

  public static void main(String[] args) throws
                                         URISyntaxException,
                                         IOException, ClassNotFoundException, NoSuchMethodException,
                                         InvocationTargetException, IllegalAccessException, InterruptedException {
    if (args.length != 1) return;
    String path = args[0].endsWith("\\") || args[0].endsWith("/") ? args[0] : args[0] + File.separator;
    if (isMac() && path.endsWith(".app/")) {
      final File file = new File(path + "Contents");
      if (file.exists() && file.isDirectory()) {
        path += "Contents/";
      }
    }

    cleanUp();

    List<URL> urls = new ArrayList<>();
    final List<File> files = new ArrayList<>();

    try (InputStream stream = Bootstrap.class.getClassLoader().getResourceAsStream("dependencies.txt")) {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
        String line;
        while ((line = br.readLine()) != null) {
          final File file = new File(path + line);
          final Path tmp = Files.createTempFile(IJ_PLATFORM_UPDATER + file.getName(), "");
          Files.copy(file.toPath(), Files.newOutputStream(tmp));
          urls.add(tmp.toFile().toURI().toURL());
          files.add(tmp.toFile());
        }
      }
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log(System.getProperty("os.name"));
      try {
        for (File file : files) {
          log("Deleting " + file.getName() + " - " + (file.delete() ? "OK" : "FAIL"));
        }
      } catch (Exception e) {
        log(e);
      }
    }));
    for (URL url : urls) {
      URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
      Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      method.setAccessible(true);
      method.invoke(classLoader, url);
    }

    final Class<?> runner = Bootstrap.class.getClassLoader().loadClass("com.intellij.updater.Runner");
    final Method main = runner.getMethod("main", String[].class);
    main.invoke(null, (Object)new String[]{"apply", args[0]});
  }

  private static void cleanUp() {
    log("Cleaning up...");
    try {
      final Path file = Files.createTempFile("", "");
      Files.list(file.getParent()).forEach((p) -> {
        if (!p.toFile().isDirectory() && p.toFile().getName().startsWith(IJ_PLATFORM_UPDATER)) try {
          log("Deleting " + p.toString());
          Files.delete(p);
        } catch (IOException e) {
          log("Can't delete " + p.toString());
          log(e);
        }
      });
      Files.delete(file);
    } catch (IOException e) {
      log(e);
    }
  }

  private static void log(String msg) {
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println(msg);
  }

  private static void log(Throwable ex) {
    //noinspection CallToPrintStackTrace
    ex.printStackTrace();
  }

  private static boolean isMac() {
    return System.getProperty("os.name").toLowerCase(Locale.US).startsWith("mac");
  }
}
