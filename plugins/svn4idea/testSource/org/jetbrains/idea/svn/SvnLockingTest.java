/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.idea.Bombed;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Ignore;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetBusyHandler;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/23/12
 * Time: 2:27 PM
 */
// TODO: Locking functionality which is tested by this test is not required anymore. Likely test needs to be removed.
@Ignore
public class SvnLockingTest extends TestCase {
  private File myWorkingCopyRoot;
  private SvnTestWriteOperationLocks myLocks;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    //PlatformTestCase.initPlatformLangPrefix();
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("svn4idea"));
    if (!pluginRoot.isDirectory()) {
      // try standalone mode
      Class aClass = Svn17TestCase.class;
      String rootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      pluginRoot = new File(rootPath).getParentFile().getParentFile().getParentFile();
    }
    myWorkingCopyRoot = new File(pluginRoot, "testData/move2unv");
    myLocks = new SvnTestWriteOperationLocks(new WorkingCopy(myWorkingCopyRoot, SVNURL.parseURIEncoded("http://a.b.c"), true));
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testPrepare() throws Exception {
    final HangInWrite operation1 = new HangInWrite("one", false);
    operation1.hang();
    operation1.go();
  }

  public void testPrepareRead() throws Exception {
    final HangInRead read = new HangInRead("READ", false);
    read.run();
    read.go();
  }

  public void testWritesSequential() throws Exception {
    final HangInWrite operation1 = new HangInWrite("one_");
    final HangInWrite operation2 = new HangInWrite("two_");

    final Thread thread1 = new Thread(operation1,"op1");
    final Thread thread2 = new Thread(operation2,"op2");
    try {
      thread1.start();
      waitForRunning(operation1);
      Assert.assertTrue(operation1.isRunning());

      thread2.start();
      waitForRunning(operation2);
      Assert.assertFalse(operation2.isRunning());

      operation1.go();
      waitForRunning(operation2);
      Assert.assertTrue(operation2.isRunning());
      operation2.go();
      Thread.sleep(10);
    } finally {
      operation1.stop();
      operation2.stop();

      thread1.interrupt();
      thread2.interrupt();
      thread1.join();
      thread2.join();
    }
  }

  public void testOnlyWrites() throws Exception {
    final OnlyWrite operation1 = new OnlyWrite("one");
    final OnlyWrite operation2 = new OnlyWrite("two");

    final Thread thread1 = new Thread(operation1,"one");
    final Thread thread2 = new Thread(operation2,"two");

    try {
      thread1.start();
      TimeoutUtil.sleep(500);
      Assert.assertTrue(operation1.isInsideWrite());
      thread2.start();
      TimeoutUtil.sleep(500);
      Assert.assertFalse(operation2.isInsideWrite());
      operation1.go();
      TimeoutUtil.sleep(500);
      Assert.assertTrue(operation2.isInsideWrite());
      operation2.go();
      TimeoutUtil.sleep(500);
    } finally {
      myLocks.dispose();
      operation1.stop();
      operation2.stop();
      Thread.sleep(100);

      thread1.interrupt();
      thread2.interrupt();
      thread1.join();
      thread2.join();
    }
  }

  /*public void testDelays() throws Exception {
    final HandlerCopy handler = new HandlerCopy(10000);
    for (int i = 0; i < 1000; i++) {
      final Pair<Boolean, Integer> pair = handler.call(i);
      System.out.println("# " + i + " DELAY: " + pair.getSecond() + " CONTINUE: " + pair.getFirst());
    }
  }

  private static class HandlerCopy {
    private static final int[] delays = { 1, 2, 5, 10, 15, 20, 25, 25,  25,  50,  50, 100 };
    private static final int[] totals = { 0, 1, 3,  8, 18, 33, 53, 78, 103, 128, 178, 228 };

    private final int timeout;

    public HandlerCopy(int timeout) {
        this.timeout = timeout;
    }

    public Pair<Boolean, Integer> call(int number) {
        int delay;
        int prior;
        if (number < delays.length) {
            delay = delays[number];
            prior = totals[number];
        } else {
            delay = delays[delays.length - 1];
            prior = totals[delays.length - 1] + delay*(number - (delays.length - 1));
        }
        if (prior + delay > timeout) {
            delay = timeout - prior;
            if (delay <= 0) {
                return new Pair<Boolean, Integer>(false, delay);
            }
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            //
        }
        return new Pair<Boolean, Integer>(true, delay);
    }
  }*/

  @Bombed(user="irengrig", year = 2020, month = 1, day = 1,
    description = "not clear. by specification, read should not get access if write lock is taken; sometimes it is not the case.")
  public void testReadInBetweenWrites() throws Exception {
    final HangInWrite operation1 = new HangInWrite("one1");
    final HangInWrite operation2 = new HangInWrite("two1");
    final HangInRead read = new HangInRead("READ");

    final Thread thread1 = new Thread(operation1,"op1");
    final Thread threadRead = new Thread(read,"read1");
    final Thread thread2 = new Thread(operation2,"op2");

    try {
      thread1.start();
      waitForRunning(operation1);
      Assert.assertTrue(operation1.isRunning());

      threadRead.start();
      waitForRunning(read);
      Assert.assertFalse(read.isRunning());     // not clear why read is allowed to run when write is active, but I've not thought it over so much

      operation1.go();
      waitForRunning(read, 20);
      Assert.assertTrue(read.isRunning());

      thread2.start();
      waitForRunning(operation2);
      Assert.assertFalse(operation2.isRunning()); // again, not clear why write is allowed to run when read is active, but I've not thought it over so much

      read.go();
      waitForRunning(operation2);
      Assert.assertTrue(operation2.isRunning());
      // have some time after read to complete
      Thread.sleep(100);
    } finally {
      myLocks.dispose();
      operation1.stop();
      operation2.stop();
      Thread.sleep(100);

      thread1.interrupt();
      thread2.interrupt();
      threadRead.interrupt();
      thread1.join();
      thread2.join();
      threadRead.join();
    }
  }

  private void waitForRunning(HangRun operation1) {
    waitForRunning(operation1, 1);
  }

  private void waitForRunning(HangRun operation1, int multiply) {
    int cnt = 10 * multiply;
    while (cnt > 0) {
      TimeoutUtil.sleep(10 * multiply);
      if (operation1.isRunning()) break;
      -- cnt;
    }
  }

  public interface HangRun {
    void go();
    boolean isRunning();
    void stop();
  }

  private class HangInRead implements Runnable, HangRun {
    private final AtomicBoolean myIsRunning;
    private final String myName;
    private final boolean myWaitFor;
    private final Semaphore mySemaphore;

    private HangInRead(String name) {
      this(name, true);
    }

    private HangInRead(String name, final boolean waitFor) {
      myName = name;
      myWaitFor = waitFor;
      myIsRunning = new AtomicBoolean(false);
      mySemaphore = new Semaphore();
    }

    @Override
    public void run() {
      mySemaphore.down();
      try {
        System.out.println("starting read " + myName);
        myLocks.wrapRead(myWorkingCopyRoot, new Runnable() {
          @Override
          public void run() {
            System.out.println("inside read " + myName);
            final SVNWCClient client = new SVNWCClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
            try {
              client.doInfo(myWorkingCopyRoot, SVNRevision.BASE);
            }
            catch (SVNException e) {
              e.printStackTrace();
              throw new RuntimeException(e);
            }
            myIsRunning.set(true);
            System.out.println("got status " + myName);
            if (myWaitFor) {
              mySemaphore.waitFor();
            }
            System.out.println("have read " + myName);
          }
        });
      }
      catch (SVNException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    @Override
    public void go() {
      System.out.println("read going " + myName);
      mySemaphore.up();
    }

    @Override
    public boolean isRunning() {
      System.out.println("read running " + myName + " " + myIsRunning.get());
      return myIsRunning.get();
    }

    @Override
    public void stop() {
    }
  }

  private class OnlyWrite extends HangInWrite {
    private OnlyWrite(String name) {
      super(name);
    }

    @Override
    public void run() {
      mySemaphore.down();
      operation();
    }
  }

  private class HangInWrite implements Runnable, HangRun {
    protected final Semaphore mySemaphore;
    private final AtomicBoolean myIsRunning;
    private final AtomicBoolean myInsideWrite;
    private final String myName;
    private boolean shouldWait;
    private volatile boolean myStopped;

    private HangInWrite(final String name) {
      this(name, true);
    }

    private HangInWrite(final String name, final boolean shouldWait) {
      myName = name;
      mySemaphore = new Semaphore();
      this.shouldWait = shouldWait;
      myIsRunning = new AtomicBoolean(false);
      myInsideWrite = new AtomicBoolean(false);
    }

    @Override
    public void run() {
      try {
        hang();
      }
      catch (SVNException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    protected void operation() {
        System.out.println("TRY OPEN FOR WRITE===");
        SqlJetDb open = null;
        try {
          open = SqlJetDb.open(SvnUtil.getWcDb(myWorkingCopyRoot), true);
          open.setBusyHandler(new ISqlJetBusyHandler() {
                      @Override
                      public boolean call(int i) {
                        if (myStopped) return false;
                        System.out.println("busy " + myName);
                        TimeoutUtil.sleep(10);
                        return true;
                      }
                    });
          try {
            System.out.println("TRY OPEN FOR WRITE " + myName);
            open.runWriteTransaction(new ISqlJetTransaction() {
              @Override
              public Object run(SqlJetDb db) throws SqlJetException {
                System.out.println("OPENed FOR WRITE " + myName);
                myInsideWrite.set(true);
                if (shouldWait) {
                  mySemaphore.waitFor();
                }
                return null;
              }
            });
          } finally {
            myInsideWrite.set(false);
            open.rollback();
          }
        }
        catch (SqlJetException e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        } catch (Exception e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
        finally {
          if (open != null) {
            try {
              open.close();
            }
            catch (SqlJetException e) {
              e.printStackTrace();
              throw new RuntimeException(e);
            }
            System.out.println("CLOSed FOR WRITE " + myName);
          }
        }
    }

    public boolean isInsideWrite() {
      return myInsideWrite.get();
    }

    public void hang() throws SVNException {
      System.out.println("starting " + myName);
      mySemaphore.down();
      myLocks.lockWrite(myWorkingCopyRoot);
      myIsRunning.set(true);
      System.out.println("started " + myName);
      try {
        operation();
      } finally {
        myLocks.unlockWrite(myWorkingCopyRoot);
        myIsRunning.set(false);
      }
    }

    public boolean isRunning() {
      System.out.println("running " + myName + " " + myIsRunning.get());
      return myIsRunning.get();
    }

    @Override
    public void stop() {
      myStopped = true;
    }

    public void go() {
      System.out.println("going " + myName);
      mySemaphore.up();
    }
  }
}
