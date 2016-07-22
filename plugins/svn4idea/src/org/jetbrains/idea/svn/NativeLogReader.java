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
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/2/12
 * Time: 4:05 PM
 */
public class NativeLogReader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.NativeLogReader");

  private final static MultiMap<Thread, CallInfo> ourCallLog = new MultiMap<Thread, CallInfo>() {
    @NotNull
    @Override
    protected Collection<CallInfo> createCollection() {
      return new ArrayList<>(2);
    }
  };
  private final static Set<Thread> ourTrackedThreads = Collections.synchronizedSet(new HashSet<Thread>());
  private final static Object ourLock = new Object();

  public static void putInfo(@NotNull final CallInfo callInfo) {
    if (ourTrackedThreads.size() > 1000) {
      for (CallInfo info : ourCallLog.values()) {
        LOG.info(SvnNativeCallsTranslator.defaultMessage(info));
      }
      LOG.warn("Too many cached Subversion native calls. Log cleared.");
      ourTrackedThreads.clear();
    }
    final Thread key = Thread.currentThread();
    if (ourTrackedThreads.contains(key)) {
      synchronized (ourLock) {
        ourCallLog.putValue(key, callInfo);
      }
    }
  }

  public static void clear() {
    final Thread key = Thread.currentThread();
    if (ourTrackedThreads.contains(key)) {
      synchronized (ourLock) {
        ourCallLog.remove(key);
      }
    }
  }

  public static List<CallInfo> getLogged() {
    final Thread key = Thread.currentThread();
    if (ourTrackedThreads.contains(key)) {
      synchronized (ourLock) {
        return (List<CallInfo>) ourCallLog.get(key);
      }
    }
    return Collections.emptyList();
  }

  public static void startTracking() {
    ourTrackedThreads.add(Thread.currentThread());
  }

  public static void endTracking() {
    ourTrackedThreads.remove(Thread.currentThread());
  }

  public static class CallInfo {
    private final String myFunctionName;
    private final int myResultCode;
    private final String myStrResultCode;

    public CallInfo(@NotNull String functionName, int resultCode) {
      myFunctionName = functionName;
      myResultCode = resultCode;
      myStrResultCode = String.valueOf(resultCode);
    }

    public CallInfo(@NotNull String functionName, @NotNull String resultCode) {
      myFunctionName = functionName;
      myResultCode = 0;
      myStrResultCode = resultCode;
    }

    public String getFunctionName() {
      return myFunctionName;
    }

    public int getResultCode() {
      return myResultCode;
    }

    public String getStrResultCode() {
      return myStrResultCode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CallInfo info = (CallInfo)o;

      if (myResultCode != info.myResultCode) return false;
      if (!myFunctionName.equals(info.myFunctionName)) return false;
      if (!myStrResultCode.equals(info.myStrResultCode)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFunctionName.hashCode();
      result = 31 * result + myResultCode;
      result = 31 * result + myStrResultCode.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "CallInfo{" +
             "myFunctionName='" + myFunctionName + '\'' +
             ", myResultCode=" + myResultCode +
             ", myStrResultCode='" + myStrResultCode + '\'' +
             '}';
    }
  }
}
