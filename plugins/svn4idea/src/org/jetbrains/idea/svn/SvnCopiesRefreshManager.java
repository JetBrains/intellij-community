/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.RequestsMerger;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;

public class SvnCopiesRefreshManager {
  private final RequestsMerger myRequestsMerger;
  private final Semaphore mySemaphore;
  private Runnable myMappingCallback;

  public SvnCopiesRefreshManager(final SvnFileUrlMappingImpl mapping) {
    mySemaphore = new Semaphore();
    // svn mappings refresh inside also uses asynchronous pass -> we need to pass callback that will ping our "single-threaded" executor here
    myMappingCallback = new Runnable() {
      @Override
      public void run() {
        mySemaphore.up();
      }
    };
    myRequestsMerger = new RequestsMerger(new Runnable() {
      @Override
      public void run() {
        mySemaphore.down();
        mapping.realRefresh(myMappingCallback);
        mySemaphore.waitFor();
      }
    }, new Consumer<Runnable>() {
      public void consume(final Runnable runnable) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable);
      }
    });
  }

  public void asynchRequest() {
    myRequestsMerger.request();
  }

  public void waitRefresh(final Runnable runnable) {
    myRequestsMerger.waitRefresh(runnable);
  }
}
