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
package org.jetbrains.idea.maven.server;

import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.WaitFor;
import com.intellij.maven.testFramework.MavenTestCase;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;

import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class MavenServerManagerTest extends MavenTestCase {

  public void testInitializingDoesntTakeReadAction() throws Exception {
    //make sure all components are initialized to prevent deadlocks
    ensureConnected(MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath()));

    Future result = ApplicationManager.getApplication().runWriteAction(
      (ThrowableComputable<Future, Exception>)() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        MavenServerManager.getInstance().shutdown(true);
        ensureConnected(MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath()));
      }));


    long start = System.currentTimeMillis();
    long end = TimeUnit.SECONDS.toMillis(10) + start;
    boolean ok = false;
    while (System.currentTimeMillis() < end && !ok) {
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      });
      try {
        result.get(0, TimeUnit.MILLISECONDS);
        ok = true;
      }
      catch (InterruptedException | java.util.concurrent.ExecutionException e) {
        throw new RuntimeException(e);
      }
      catch (TimeoutException ignore) {
      }
    }
    if (!ok) {
      printThreadDump();
      fail();
    }
    result.cancel(true);
  }

  public void testConnectorRestartAfterVMChanged() {
    MavenWorkspaceSettingsComponent settingsComponent = MavenWorkspaceSettingsComponent.getInstance(myProject);
    String vmOptions = settingsComponent.getSettings().getImportingSettings().getVmOptionsForImporter();
    try {
      MavenServerConnector connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());
      ensureConnected(connector);
      settingsComponent.getSettings().getImportingSettings().setVmOptionsForImporter(vmOptions + " -DtestVm=test");
      assertNotSame(connector, ensureConnected(MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath())));
    }
    finally {
      settingsComponent.getSettings().getImportingSettings().setVmOptionsForImporter(vmOptions);
    }
  }

  public void testShouldRestartConnectorAutomaticallyIfFailed() {
    MavenServerConnector connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());
    ensureConnected(connector);
    kill(connector);
    MavenServerConnector newConnector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());
    ensureConnected(newConnector);
    assertNotSame(connector, newConnector);
  }



  public void testShouldStopPullingIfConnectorIsFailing() {
    MavenServerConnector connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());
    ensureConnected(connector);
    ScheduledFuture loggerFuture =
      ReflectionUtil.getField(MavenServerConnectorImpl.class, connector, ScheduledFuture.class, "myLoggerFuture");
    kill(connector);
    new WaitFor(1_000) {
      @Override
      protected boolean condition() {
        return loggerFuture.isCancelled();
      }
    };
    assertTrue(loggerFuture.isCancelled());
  }

  public void testShouldDropConnectorForMultiplyDirs() {
    File topDir = myProjectRoot.toNioPath().toFile();
    File first = new File(topDir, "first/.mvn");
    File second = new File(topDir, "second/.mvn");
    assertTrue(first.mkdirs());
    assertTrue(second.mkdirs());
    MavenServerConnector connectorFirst = MavenServerManager.getInstance().getConnector(myProject, first.getAbsolutePath());
    ensureConnected(connectorFirst);
    MavenServerConnector connectorSecond = MavenServerManager.getInstance().getConnector(myProject, second.getAbsolutePath());
    assertSame(connectorFirst, connectorSecond);
    MavenServerManager.getInstance().cleanUp(connectorFirst);
    assertEmpty(MavenServerManager.getInstance().getAllConnectors());
    connectorFirst.shutdown(true);
  }

  private static void kill(MavenServerConnector connector) {
    RemoteProcessSupport support =
      ReflectionUtil.getField(MavenServerConnectorImpl.class, connector, RemoteProcessSupport.class, "mySupport");
    AtomicReference<RemoteProcessSupport.Heartbeat> heartbeat =
      ReflectionUtil.getField(RemoteProcessSupport.class, support, AtomicReference.class, "myHeartbeatRef");
    heartbeat.get().kill(1);
    new WaitFor(10_000) {
      @Override
      protected boolean condition() {
        return !connector.checkConnected();
      }
    };
    assertFalse(connector.checkConnected());
  }
}
