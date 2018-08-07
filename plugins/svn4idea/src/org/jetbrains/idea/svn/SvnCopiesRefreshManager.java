// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.RequestsMerger;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.TestOnly;

public class SvnCopiesRefreshManager {
  private final RequestsMerger myRequestsMerger;
  private final Semaphore mySemaphore;
  private final Runnable myMappingCallback;

  public SvnCopiesRefreshManager(final SvnFileUrlMappingImpl mapping) {
    mySemaphore = new Semaphore();
    // svn mappings refresh inside also uses asynchronous pass -> we need to pass callback that will ping our "single-threaded" executor here
    myMappingCallback = () -> mySemaphore.up();
    myRequestsMerger = new RequestsMerger(() -> {
      mySemaphore.down();
      mapping.realRefresh(myMappingCallback);
      mySemaphore.waitFor();
    }, runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable));
  }

  public void asynchRequest() {
    myRequestsMerger.request();
  }

  public void waitRefresh(final Runnable runnable) {
    myRequestsMerger.waitRefresh(runnable);
  }

  @TestOnly
  void waitCurrentRequest() {
    mySemaphore.waitFor();
  }
}
