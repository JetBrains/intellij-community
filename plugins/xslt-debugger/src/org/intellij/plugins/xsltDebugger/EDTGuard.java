/*
 * Copyright 2002-2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Alarm;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.intellij.plugins.xsltDebugger.rt.engine.Watchable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class that tries to prevent hanging the whole IDE because some call on the EDT waits too long on the debugged
 * VM, which can especially happen when the VM has been paused (either manually or by a breakpoint on Java code).
 * <p/>
 * This is just the second-best solution though as it would be better to avoid any interaction with the debuggee on the
 * EDT, but, at least right now, it seems to be more reliable against bad surprises.
 */
final class EDTGuard implements InvocationHandler {
  // maximum time to wait for a result on the EDT
  private static final long MAX_TIMEOUT = 10 * 1000;

  private final Map<Object, Object> myInstanceCache = new Reference2ObjectOpenHashMap<>();

  private final Object myTarget;

  private final Pair<LinkedBlockingQueue<Call>, LinkedBlockingQueue<Call.Result>> myQueue;
  private final AtomicBoolean myPausedRef;

  private EDTGuard(Object target,
                   Pair<LinkedBlockingQueue<Call>, LinkedBlockingQueue<Call.Result>> queue,
                   AtomicBoolean ref) {
    myTarget = target;
    myQueue = queue;
    myPausedRef = ref;
  }

  @Override
  public @Nullable Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
    if (SwingUtilities.isEventDispatchThread()) {
      return invokeAsync(method, args);
    }

    return invoke(method, args);
  }

  private @Nullable Object invokeAsync(Method method, Object[] args) throws Throwable {
    final Call call = new Call(method, args);

    if (!myQueue.first.offer(call)) {
      throw new VMPausedException();
    }

    Call.Result result;
    final long start = System.currentTimeMillis();
    do {
      do {
        result = myQueue.second.poll(200, TimeUnit.MILLISECONDS);

        if (myPausedRef.get() || result == null && System.currentTimeMillis() - start > MAX_TIMEOUT) {
          throw new VMPausedException();
        }
      }
      while (result == null);
    }
    while (!result.isFromCall(call));

    return result.getValue();
  }

  private @Nullable Object invoke(@NotNull Method method, Object[] args) throws Throwable {
    try {
      return convert(method.invoke(myTarget, args));
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }

  @SuppressWarnings("unchecked")
  private @Nullable Object convert(@Nullable Object o) {
    if (o != null && !(o instanceof Serializable)) {
      synchronized (myInstanceCache) {
        Object instance = myInstanceCache.get(o);
        if (instance == null) {
          final ClassLoader loader = o.getClass().getClassLoader();
          final Class<?>[] interfaces = o.getClass().getInterfaces();
          final EDTGuard guard = new EDTGuard(o, myQueue, myPausedRef);

          myInstanceCache.put(o, instance = Proxy.newProxyInstance(loader, interfaces, guard));
        }
        return instance;
      }
    } else if (o instanceof List) {
      final List<Object> list = (List<Object>)o;
      for (int i = 0; i < list.size(); i++) {
        final Object e = list.remove(i);
        list.add(i, convert(e));
      }
    } else if (o instanceof Set) {
      final Set<Object> set = (Set<Object>)o;
      final List<Object> s2 = new ArrayList<>();
      for (Iterator<Object> iterator = set.iterator(); iterator.hasNext(); ) {
        Object o1 = iterator.next();
        final Object o2 = convert(o1);
        if (o1 != o2) {
          iterator.remove();
          s2.add(o2);
        }
      }
      set.addAll(s2);
    }
    return o;
  }

  public static @NotNull <T, O extends Watchable> T create(final @NotNull O target, final ProcessHandler process) {
    final Pair<LinkedBlockingQueue<Call>, LinkedBlockingQueue<Call.Result>> queue =
      Pair.create(new LinkedBlockingQueue<>(10), new LinkedBlockingQueue<>());

    final Thread thread = new Thread("Async Invocation Thread for " + process) {
      @Override
      public void run() {
        try {
          while (!Thread.currentThread().isInterrupted()) {
            final Call call = queue.first.take();
            queue.second.offer(call.invoke());
          }
        } catch (InterruptedException e) {
          // break
        }
      }
    };
    thread.start();

    final AtomicBoolean ref = new AtomicBoolean();
    final Disposable d = new Disposable() {
      boolean disposed;

      @Override
      public void dispose() {
        if (!disposed) {
          disposed = true;

          ref.set(true);
          thread.interrupt();
        }
      }
    };
    process.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        synchronized (d) {
          Disposer.dispose(d);
        }
      }

      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        if (!willBeDestroyed) {
          synchronized (d) {
            Disposer.dispose(d);
          }
        }
      }
    });

    final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, d);
    final Alarm alarm2 = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, alarm);

    final Runnable watchdog = () -> ref.set(true);

    final Runnable ping = new Runnable() {
      @Override
      public void run() {
        synchronized (d) {
          if (alarm.isDisposed()) {
            return;
          }

          alarm2.addRequest(watchdog, 200);
          try {
            ref.set(!target.ping());
          } catch (Exception e) {
            ref.set(true);
          } finally {
            alarm2.cancelRequest(watchdog);
            alarm.addRequest(this, 500);
          }
        }
      }
    };
    alarm.addRequest(ping, 500);

    final EDTGuard guard = new EDTGuard(target, queue, ref);
    final ClassLoader classLoader = target.getClass().getClassLoader();
    final Class<?>[] interfaces = target.getClass().getInterfaces();
    //noinspection unchecked
    return (T)Proxy.newProxyInstance(classLoader, interfaces, guard);
  }

  class Call {
    private final Method myMethod;
    private final Object[] myArguments;

    class Result {
      private final Object myObject;
      private final Throwable myThrowable;

      Result(Object o) {
        myObject = o;
        myThrowable = null;
      }

      Result(Throwable o) {
        myObject = null;
        myThrowable = o;
      }

      public boolean isFromCall(Call call) {
        return call == Call.this;
      }

      public @Nullable Object getValue() throws Throwable {
        if (myThrowable != null) {
          throw myThrowable;
        }
        return myObject;
      }
    }

    Call(Method method, Object[] arguments) {
      myMethod = method;
      myArguments = arguments;
    }

    public @NotNull Result invoke() {
      try {
        return new Result(EDTGuard.this.invoke(myMethod, myArguments));
      } catch (Throwable e) {
        return new Result(e);
      }
    }
  }
}
