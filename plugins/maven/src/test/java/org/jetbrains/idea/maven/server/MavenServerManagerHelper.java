/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.impl.ApplicationImpl;

import java.rmi.RemoteException;

public class MavenServerManagerHelper {
  /** {@link MavenServerManager#collectClassPathAndLibsFolder()} forces maven2 in test mode; we want maven3. */
  public static MavenServer connectToMaven3Server() throws RemoteException {
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    MavenServerManager msm = MavenServerManager.getInstance(); // do this first, it won't work outside of unit test mode

    msm.cleanup(); // in case we were previously connected to maven2

    boolean oldUnitTestMode = application.isUnitTestMode();
    application.setUnitTestMode(false);
    try {
      return msm.getOrCreateWrappee();
    }
    finally {
      application.setUnitTestMode(oldUnitTestMode);
    }
  }

  public static void disconnectFromServer() {
    MavenServerManager msm = MavenServerManager.getInstance();
    msm.cleanup();
  }
}
