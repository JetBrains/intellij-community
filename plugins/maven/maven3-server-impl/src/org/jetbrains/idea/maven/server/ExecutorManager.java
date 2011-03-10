/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorManager {
  private static final ExecutorService myExecutor = new ThreadPoolExecutor(3, Integer.MAX_VALUE, 30 * 60L, TimeUnit.SECONDS,
                                                                           new SynchronousQueue<Runnable>(),new ThreadFactory() {
      AtomicInteger num = new AtomicInteger();
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "Maven Embedder "+num.getAndIncrement());
      }
    });

  public static Future<?> execute(Runnable r) {
    return myExecutor.submit(r);
  }
}
