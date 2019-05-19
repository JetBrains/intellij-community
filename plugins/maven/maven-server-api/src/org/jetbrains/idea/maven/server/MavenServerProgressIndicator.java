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

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MavenServerProgressIndicator extends Remote {
  String DEPENDENCIES_RESOLVE_PREFIX = "Resolving Maven dependencies--";
  String PLUGINS_RESOLVE_PREFIX = "Downloading Maven plugins--";
  void setText(String text) throws RemoteException;
  void setText2(String text) throws RemoteException;
  void startTask(String text) throws RemoteException;
  void completeTask(String text, String errorMessage) throws RemoteException;

  boolean isCanceled() throws RemoteException;

  void setIndeterminate(boolean value) throws RemoteException;

  void setFraction(double fraction) throws RemoteException;
}
