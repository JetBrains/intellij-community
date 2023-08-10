// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class MavenRemotePullUtil {
  @NotNull
  static <T> List<T> pull(Queue<T> queue) {
    List<T> result = new ArrayList<T>();
    T last = queue.poll();
    if(last == null) return result;
    result.add(last);
    while((last = queue.poll())!=null) {
      result.add(last);
    }
    return result;
  }
}
