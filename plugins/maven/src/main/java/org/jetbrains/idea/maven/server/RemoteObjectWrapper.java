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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.rmi.RemoteException;

public abstract class RemoteObjectWrapper<T> {
  @Nullable private final RemoteObjectWrapper<?> myParent;
  @Nullable private T myWrappee;

  protected RemoteObjectWrapper(@Nullable RemoteObjectWrapper<?> parent) {
    myParent = parent;
  }

  @Nullable
  protected synchronized T getWrappee() {
    return myWrappee == null ? null : myWrappee;
  }

  @NotNull
  protected synchronized T getOrCreateWrappee() throws RemoteException {
    if (myWrappee == null) {
      myWrappee = create();
      onWrappeeCreated();
    }
    onWrappeeAccessed();
    return myWrappee;
  }

  @NotNull
  protected abstract T create() throws RemoteException;

  protected void onWrappeeCreated() throws RemoteException {
  }

  protected void onWrappeeAccessed() {
    if (myParent != null) myParent.onWrappeeAccessed();
  }

  protected synchronized void handleRemoteError(RemoteException e) {
    MavenLog.LOG.debug("Connection failed. Will be reconnected on the next request.", e);
    onError();
  }

  protected synchronized void onError() {
    cleanup();
    if (myParent != null) myParent.onError();
  }

  protected synchronized void cleanup() {
    myWrappee = null;
  }

  protected <T> T perform(Retriable<T> r) {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        handleRemoteError(last = e);
      }
    }
    throw new RuntimeException("Cannot reconnect.", last);
  }

  protected <T> T perform(RetriableCancelable<T> r) throws MavenProcessCanceledException {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        handleRemoteError(last = e);
      }
      catch (MavenServerProcessCanceledException e) {
        throw new MavenProcessCanceledException();
      }
    }
    throw new RuntimeException("Cannot reconnect.", last);
  }

  protected <T> T perform(IndexRetriable<T> r) throws MavenServerIndexerException {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        handleRemoteError(last = e);
      }
    }
    throw new RuntimeException("Cannot reconnect.", last);
  }

  protected <T> T perform(IndexRetriableCancelable<T> r) throws MavenServerIndexerException, MavenProcessCanceledException {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        handleRemoteError(last = e);
      }
      catch (MavenServerProcessCanceledException e) {
        throw new MavenProcessCanceledException();
      }
    }
    throw new RuntimeException("Cannot reconnect.", last);
  }

  protected interface Retriable<T> {
    T execute() throws RemoteException;
  }

  protected interface IndexRetriable<T> {
    T execute() throws RemoteException, MavenServerIndexerException;
  }

  protected interface IndexRetriableCancelable<T> {
    T execute() throws RemoteException, MavenServerIndexerException, MavenServerProcessCanceledException;
  }

  protected interface RetriableCancelable<T> {
    T execute() throws RemoteException, MavenServerProcessCanceledException;
  }
}
