// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MavenServerStatsCollector {
  private static final Map<String, AtomicInteger> readCounters = new ConcurrentHashMap<String, AtomicInteger>();
  private static final Map<String, AtomicInteger> pluginResolving = new ConcurrentHashMap<String, AtomicInteger>();
  public static final boolean collectStatistics = Boolean.getBoolean("maven.collect.stat");

  private MavenServerStatsCollector() { }

  public static void fileRead(File file) {
    if (!collectStatistics) return;
    String path = file.getAbsolutePath();
    putOrAdd(path, readCounters);
  }

  private static void putOrAdd(String path, Map<String, AtomicInteger> countMap) {
    AtomicInteger counter = countMap.get(path);
    if (counter == null) {
      counter = countMap.put(path, new AtomicInteger(1));
    }
    if (counter != null) {
      counter.incrementAndGet();
    }
  }

  private static void fill(Map<String, Integer> dest, Map<String, AtomicInteger> src) {
    src.entrySet().forEach(e -> {
      dest.put(e.getKey(), e.getValue().get());
    });
  }

  static void pluginResolve(String mavenid) {
    if (!collectStatistics) return;
    putOrAdd(mavenid, pluginResolving);
  }

  static void fill(MavenServerStatus status, boolean clean) {
    fill(status.fileReadAccessCount, readCounters);
    fill(status.pluginResolveCount, pluginResolving);
  }
}
