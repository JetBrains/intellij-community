// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.ExceptionUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.server.security.MavenToken;
import org.jetbrains.idea.maven.server.security.TokenReader;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MavenServerUtil {
  private static final Properties mySystemPropertiesCache;
  private static MavenToken ourToken;



  static {
    Properties res = new Properties();
    res.putAll((Properties)System.getProperties().clone());

    for (Iterator<Object> itr = res.keySet().iterator(); itr.hasNext(); ) {
      String propertyName = itr.next().toString();
      if (propertyName.startsWith("idea.")) {
        itr.remove();
      }
    }

    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      String key = entry.getKey();

      if (isMagicalProperty(key)) continue;

      if (SystemInfoRt.isWindows) {
        key = key.toUpperCase(Locale.ENGLISH);
      }

      res.setProperty("env." + key, entry.getValue());
    }

    mySystemPropertiesCache = res;
  }

  public static Properties collectSystemProperties() {
    return mySystemPropertiesCache;
  }

  public static @NotNull File findMavenBasedir(@NotNull File workingDir) {
    File baseDir = workingDir;
    File dir = workingDir;
    while (dir != null) {
      MavenServerStatsCollector.fileRead(new File(dir, ".mvn"));
      if (new File(dir, ".mvn").exists()) {
        baseDir = dir;
        break;
      }
      dir = dir.getParentFile();
    }
    try {
      return baseDir.getCanonicalFile();
    }
    catch (IOException e) {
      return baseDir.getAbsoluteFile();
    }
  }


  private static boolean isMagicalProperty(String key) {
    return key.startsWith("=");
  }

  public static void registerShutdownTask(Runnable task) {
    Runtime.getRuntime().addShutdownHook(new Thread(task, "Maven-server-shutdown-hook"));
  }

  @TestOnly
  public static void addProperty(String propertyName, String value) {
    mySystemPropertiesCache.setProperty(propertyName, value);
  }

  @TestOnly
  public static void removeProperty(String propertyName) {
    mySystemPropertiesCache.remove(propertyName);
  }

  public static void checkToken(MavenToken token) throws SecurityException {
    if (ourToken == null || !ourToken.equals(token)) {
      throw new SecurityException();
    }
  }

  public static MavenToken getToken() {
    return ourToken;
  }

  public static void readToken() {
    try {
      ourToken = new TokenReader(new Scanner(System.in), 10000).getToken();
    } catch (Throwable e) {
      ExceptionUtilRt.rethrowUnchecked(e);
    }
  }
}