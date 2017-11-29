/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class SvnBusyOnAddTest extends TestCase {
  public static final String filename = "abc/test.txt";

  private File myWorkingCopyRoot;

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
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testRefusedAdd ()throws Exception {
    SVNWCDb db = new SVNWCDb();
    final File ioFile = new File(myWorkingCopyRoot, filename);
    ioFile.getParentFile().mkdirs();
    ioFile.createNewFile();
    try {
      db.open(ISVNWCDb.SVNWCDbOpenMode.ReadWrite, new DefaultSVNOptions(), true, true);
      SVNWCContext context = new SVNWCContext(db, new ISVNEventHandler() {
        @Override
        public void handleEvent(SVNEvent event, double progress) {
        }

        @Override
        public void checkCancelled() {
        }
      });

      File file = context.acquireWriteLock(myWorkingCopyRoot, false, true);
      boolean failed = false;
      try {
        SVNWCClient client = new SVNWCClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
        client.doAdd(ioFile, true, false, false, true);
      }
      catch (SVNException e) {
        Assert.assertEquals(155004, e.getErrorMessage().getErrorCode().getCode());
        failed = true;
      }
      finally {
        context.releaseWriteLock(myWorkingCopyRoot);
      }
      Assert.assertTrue(failed);

      SVNStatusClient readClient = new SVNStatusClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      //readClient.doStatus(ioFile, false);
      readClient.doStatus(myWorkingCopyRoot, false);
    }
    finally {
      ioFile.delete();
      db.close();
    }
  }

  public void testStatusDoesNotLockForWrite() throws Exception {
    final File ioFile = new File(myWorkingCopyRoot, filename);
    ioFile.getParentFile().mkdirs();

    /*SVNWCClient client11 = new SVNWCClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
    client11.doAdd(ioFile.getParentFile(), true, false, true, true);*/

    ioFile.createNewFile();
    try {
      final SVNStatusClient readClient = new SVNStatusClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      final Semaphore semaphore = new Semaphore();
      final Semaphore semaphoreMain = new Semaphore();
      final Semaphore semaphoreWokeUp = new Semaphore();

      final AtomicReference<Boolean> wasUp = new AtomicReference<>(false);
      final ISVNStatusHandler handler = status -> {
        semaphore.waitFor();
        wasUp.set(true);
      };

      semaphore.down();
      semaphoreMain.down();
      semaphoreWokeUp.down();

      final SVNException[] exception = new SVNException[1];
      Thread thread = new Thread(() -> {
        try {
          semaphoreMain.up();
          readClient.doStatus(myWorkingCopyRoot, true, false, true, false, handler);
          semaphoreWokeUp.up();
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }, "svn test");
      thread.start();

      semaphoreMain.waitFor();
      TimeoutUtil.sleep(5);
      SVNWCClient client = new SVNWCClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
      client.doAdd(ioFile.getParentFile(), true, false, true, true);
      semaphore.up();
      semaphoreWokeUp.waitFor();

      Assert.assertEquals(true, wasUp.get().booleanValue());
      if (exception[0] != null) {
        throw exception[0];
      }
      thread.join();
    }
    finally {
      ioFile.delete();
    }
  }

  @Test
  public void testRefusedAddVariant ()throws Exception {
    SVNWCDb db = new SVNWCDb();
    final File ioFile = new File(myWorkingCopyRoot, filename + System.currentTimeMillis());
    ioFile.createNewFile();

    System.out.println(getStatus(ioFile));

    SVNWCContext context = null;
    try {
      db.open(ISVNWCDb.SVNWCDbOpenMode.ReadWrite, new DefaultSVNOptions(), true, true);
      context = new SVNWCContext(db, new ISVNEventHandler() {
        @Override
        public void handleEvent(SVNEvent event, double progress) {
        }

        @Override
        public void checkCancelled() {
        }
      });

      File file = context.acquireWriteLock(myWorkingCopyRoot, false, true);
      boolean failed = false;
      try {
        SVNWCClient client = new SVNWCClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
        client.doAdd(ioFile, true, false, false, true);
      }
      catch (SVNException e) {
        Assert.assertEquals(155004, e.getErrorMessage().getErrorCode().getCode());
        failed = true;
      }
      Assert.assertTrue(failed);

      System.out.println(getStatus(ioFile));
    }
    finally {
      if (context != null) {
        context.releaseWriteLock(myWorkingCopyRoot);
      }
      ioFile.delete();
      db.close();
    }
  }

  @Nullable
  private String getStatus(final File ioFile) throws SVNException {
    try {
    SVNStatusClient readClient = new SVNStatusClient((ISVNRepositoryPool)null, new DefaultSVNOptions());
    final SVNStatus status = readClient.doStatus(ioFile, false);
    return status == null ? null : status.getNodeStatus().toString();
    } catch (SVNException e) {
      if (SVNErrorCode.WC_NOT_WORKING_COPY.equals(e.getErrorMessage().getErrorCode())) {
        return null;
      }
      throw e;
    }
  }
}
