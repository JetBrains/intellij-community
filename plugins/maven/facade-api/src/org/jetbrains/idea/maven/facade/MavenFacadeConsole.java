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
package org.jetbrains.idea.maven.facade;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MavenFacadeConsole extends Remote {
  // must be same as in org.codehaus.plexus.logging.Logger
  int LEVEL_DEBUG = 0;
  int LEVEL_INFO = 1;
  int LEVEL_WARN = 2;
  int LEVEL_ERROR = 3;
  int LEVEL_FATAL = 4;
  int LEVEL_DISABLED = 5;

  void printMessage(int level, String message, Throwable throwable) throws RemoteException;
}
