// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.RequestsMerger;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.TestOnly;

public class SvnCopiesRefreshManager {
  private final RequestsMerger myRequestsMerger;
  private final Semaphore mySemaphore = new Semaphore();

  public SvnCopiesRefreshManager(final SvnFileUrlMappingImpl mapping) {
    myRequestsMerger = new RequestsMerger(() -> {
      mySemaphore.down();
      mapping.realRefresh();
      mySemaphore.up();
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
