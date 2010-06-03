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
package org.jetbrains.idea.maven.facade.embedder;

import org.jetbrains.idea.maven.facade.MavenFacadeLogger;
import org.jetbrains.idea.maven.facade.RemoteObject;

import java.rmi.RemoteException;

public class MavenFacadeLoggerWrapper {
  MavenFacadeLogger myWrappee;

  public MavenFacadeLoggerWrapper(MavenFacadeLogger wrappee) {
    myWrappee = wrappee;
  }

  public void info(Throwable e)  {
    try {
      myWrappee.info(RemoteObject.wrapException(e));
    }
    catch (RemoteException e1) {
      throw new RuntimeException(e1);
    }
  }

  public void warn(Throwable e)  {
    try {
      myWrappee.warn(RemoteObject.wrapException(e));
    }
    catch (RemoteException e1) {
      throw new RuntimeException(e1);
    }
  }

  public void error(Throwable e) {
    try {
      myWrappee.error(RemoteObject.wrapException(e));
    }
    catch (RemoteException e1) {
      throw new RuntimeException(e1);
    }
  }
}
