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

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.idea.maven.MavenTestCase;

import java.rmi.RemoteException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MavenServerManagerTest extends MavenTestCase {
  public void testInitializingDoesntTakeReadAction() throws Exception {
    //make sure all components are initialized to prevent deadlocks
    MavenServerManager.getInstance().getOrCreateWrappee();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Future result = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            MavenServerManager.getInstance().shutdown(true);
            try {
              MavenServerManager.getInstance().getOrCreateWrappee();
            }
            catch (RemoteException e) {
              throw new RuntimeException(e);
            }
          }
        });

        try {
          result.get(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        catch (java.util.concurrent.ExecutionException e) {
          throw new RuntimeException(e);
        }
        catch (TimeoutException e) {
          printThreadDump();
          throw new RuntimeException(e);
        }
        result.cancel(true);
      }
    });
  }
}
