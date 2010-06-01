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

import org.jetbrains.idea.maven.facade.MavenFacadeProgressIndicator;

import java.rmi.RemoteException;

public class MavenFacadeProgressIndicatorWrapper {
  private final MavenFacadeProgressIndicator myWrappee;

  public MavenFacadeProgressIndicatorWrapper(MavenFacadeProgressIndicator wrappee) {
    myWrappee = wrappee;
  }

  public void setText(String text) {
    try {
      myWrappee.setText(text);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void setText2(String text) {
    try {
      myWrappee.setText2(text);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isCanceled() {
    try {
      return myWrappee.isCanceled();
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void checkCanceled() {
    if (isCanceled()) throw new RuntimeCanceledException();
  }

  public void setIndeterminate(boolean value) {
    try {
      myWrappee.setIndeterminate(value);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public void setFraction(double fraction) {
    try {
      myWrappee.setFraction(fraction);
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public static class RuntimeCanceledException extends RuntimeException {
  }
}
