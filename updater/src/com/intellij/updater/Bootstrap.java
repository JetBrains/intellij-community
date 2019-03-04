// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author Konstantin Bulenkov
 */
public class Bootstrap {
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

    String path = args[0].endsWith("\\") || args[0].endsWith("/") ? args[0] : args[0] + File.separator;
    if (isMac() && path.endsWith(".app/")) {
      File file = new File(path + "Contents");
      if (file.exists() && file.isDirectory()) {
        path += "Contents/";
      }
    }

    ClassLoader cl = Bootstrap.class.getClassLoader();
    URL dependenciesTxt = cl.getResource("dependencies.txt");
    if (dependenciesTxt == null) throw new Exception("Missing dependencies.txt file in classpath");

    Map<String, byte[]> classes = new HashMap<>();
    for (File dependencyFile : readDependenciesTxt(path, dependenciesTxt)) {
      // Load dependency JARs in memory not to lock them on disk
      collectClassesFromJar(dependencyFile, classes);
    }

    URL jarsInMemoryUrl = createInMemoryUrlClassesRoot(classes);
    URL updaterUrl = Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();

    try (URLClassLoader loader = new URLClassLoader(new URL[]{updaterUrl, jarsInMemoryUrl}, null)) {
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

  private static void collectClassesFromJar(File jarFile, Map<String, byte[]> classes) throws IOException {
    byte[] buffer = new byte[1024];

    try (InputStream fileInputStream = new FileInputStream(jarFile);
         JarInputStream is = new JarInputStream(fileInputStream)) {
      JarEntry nextEntry;
      while ((nextEntry = is.getNextJarEntry()) != null) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.max((int)nextEntry.getSize(), 1024));

        int len;
        while ((len = is.read(buffer)) > 0) {
          outputStream.write(buffer, 0, len);
        }

        classes.put("/" + nextEntry.getName(), outputStream.toByteArray());
      }
    }
  }

  private static URL createInMemoryUrlClassesRoot(Map<String, byte[]> classes) throws MalformedURLException {
    return new URL("x-in-memory", null, -1, "/", new URLStreamHandler() {
      @Override
      protected URLConnection openConnection(URL u) throws IOException {
        final byte[] data = classes.get(u.getFile());
        if (data == null) {
          throw new FileNotFoundException(u.getFile());
        }

        return new URLConnection(u) {
          @Override
          public void connect() {
          }

          @Override
          public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
          }
        };
      }
    });
  }

  private static List<File> readDependenciesTxt(String basePath, URL dependenciesTxtUrl) throws Exception {
    List<File> files = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(new InputStreamReader(dependenciesTxtUrl.openStream()))) {
      String line;
      while ((line = br.readLine()) != null) {
        File file = new File(basePath + line);
        if (!file.exists()) throw new Exception("File from dependencies.txt is not found: " + file);

        files.add(file);
      }
    }

    return files;
  }

  private static boolean isMac() {
    return System.getProperty("os.name").toLowerCase(Locale.US).startsWith("mac");
  }
}