package org.jetbrains.idea.svn17;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.EnsureUpToDateFromNonAWTThread;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;


public class VcsWaitForUpdateForTest extends SvnTestCase {
  @Test
  public void testRefreshes() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final Object lock = new Object();

    final Ref<Boolean> done = new Ref<Boolean>();
    final Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        new EnsureUpToDateFromNonAWTThread(myProject).execute();
        done.set(Boolean.TRUE);
        synchronized (lock) {
          lock.notifyAll();
        }
      }
    });

    thread.start();
    synchronized (lock) {
      final long start = System.currentTimeMillis();
      final int timeout = 3000;

      while ((System.currentTimeMillis() - start < timeout) && (! Boolean.TRUE.equals(done.get()))) {
        try {
          lock.wait(timeout);
        }
        catch (InterruptedException e) {
          //
        }
      }
    }

    assert Boolean.TRUE.equals(done.get());
  }
}
