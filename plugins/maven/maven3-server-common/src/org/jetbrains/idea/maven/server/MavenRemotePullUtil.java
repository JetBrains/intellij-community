// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class MavenRemotePullUtil {
    static <T> List<T> pull(Queue<T> queue) {
      T last = queue.poll();
      if(last == null) return null;
      List<T> result = new ArrayList<T>();
      result.add(last);
      while((last = queue.poll())!=null) {
        result.add(last);
      }
      return result;
    }
}
