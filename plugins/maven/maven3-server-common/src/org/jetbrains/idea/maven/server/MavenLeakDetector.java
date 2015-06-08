/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.ReflectionUtil;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class MavenLeakDetector {

  private IdentityHashMap<Thread, Thread> markedHooks = new IdentityHashMap<Thread, Thread>();

  public MavenLeakDetector mark() {
    markShutdownHooks();
    return this;
  }

  private void markShutdownHooks() {
    markedHooks.putAll(getShutdownHooks());
  }

  public void check() throws RemoteException {
    checkShutdownHooks();
  }

  private void checkShutdownHooks() throws RemoteException {
    IdentityHashMap<Thread, Thread> checkedHooks = new IdentityHashMap<Thread, Thread>(getShutdownHooks());
    for (Thread t : markedHooks.values()) {
      checkedHooks.remove(t);
    }
    for (Thread t : checkedHooks.values()) {
      removeHook(t);
    }
  }

  private void removeHook(Thread thread) throws RemoteException {
    Runtime.getRuntime().removeShutdownHook(thread);
    Maven3ServerGlobals.getLogger().print(String.format("ShutdownHook[%s] was removed to avoid memory leak", thread));
  }

  private Map<Thread, Thread> getShutdownHooks() {
    Class clazz;
    try {
      clazz = Class.forName("java.lang.ApplicationShutdownHooks");
    }
    catch (ClassNotFoundException e) {
      // we can ignore this one
      return Collections.emptyMap();
    }
    return ReflectionUtil.getStaticFieldValue(clazz, Map.class, "hooks");
  }
}
