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
package org.jetbrains.idea.maven.server.embedder;

import org.jetbrains.idea.maven.server.MavenRemoteObject;
import org.jetbrains.idea.maven.server.MavenServerLogger;

import java.rmi.RemoteException;

public class Maven2ServerLoggerWrapper extends MavenRemoteObject {
  MavenServerLogger myWrappee;

  public Maven2ServerLoggerWrapper(MavenServerLogger wrappee) {
    myWrappee = wrappee;
  }

  public void info(Throwable e) throws RemoteException {
    myWrappee.info(wrapException(e));
  }

  public void warn(Throwable e) throws RemoteException {
    myWrappee.warn(wrapException(e));
  }

  public void error(Throwable e) throws RemoteException {
    myWrappee.error(wrapException(e));
  }

  public void print(String o) throws RemoteException {
    myWrappee.print(o);
  }
}
