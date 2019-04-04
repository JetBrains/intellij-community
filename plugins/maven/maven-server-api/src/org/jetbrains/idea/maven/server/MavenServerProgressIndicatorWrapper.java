/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.rmi.RemoteException;

/**
 * @author Sergey Evdokimov
 */
public class MavenServerProgressIndicatorWrapper implements MavenServerProgressIndicator {

  private final MavenServerProgressIndicator myDelegate;

  private boolean myCanceled;

  private Boolean myIndeterminate;

  private String myText2;

  public MavenServerProgressIndicatorWrapper(MavenServerProgressIndicator delegate) {
    myDelegate = delegate;
  }

  @Override
  public void setText(String text) throws RemoteException {
    myDelegate.setText(text);
  }

  @Override
  public void setText2(String text) throws RemoteException {
    if (text.equals(myText2)) {
      return;
    }

    myText2 = text;
    myDelegate.setText2(text);
  }

  @Override
  public boolean isCanceled() throws RemoteException {
    if (myCanceled) {
      return true;
    }

    if (myDelegate.isCanceled()) {
      myCanceled = true;
      return true;
    }

    return false;
  }

  @Override
  public void setIndeterminate(boolean value) throws RemoteException {
    if (myIndeterminate != null && myIndeterminate == value) {
      return;
    }

    myIndeterminate = value;

    myDelegate.setIndeterminate(value);
  }

  @Override
  public void setFraction(double fraction) throws RemoteException {
    myDelegate.setFraction(fraction);
  }
}
