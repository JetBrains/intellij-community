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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

public class SvnFileSystemListenerWrapper {
  private final LocalFileOperationsHandler myProxy;
  private final MyCommandListener myListener;

  public SvnFileSystemListenerWrapper(final SvnFileSystemListener delegate) {
    myListener = new MyCommandListener(delegate);
    myProxy = (LocalFileOperationsHandler)Proxy
      .newProxyInstance(LocalFileOperationsHandler.class.getClassLoader(), new Class<?>[]{LocalFileOperationsHandler.class},
                        new MyInvoker(new MyStorage(myListener), delegate));
  }

  public void registerSelf() {
    LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(myProxy);
    CommandProcessor.getInstance().addCommandListener(myListener);
  }

  public void unregisterSelf() {
    LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(myProxy);
    CommandProcessor.getInstance().removeCommandListener(myListener);
  }

  private static class MyCommandListener extends CommandAdapter {
    private volatile boolean myInCommand;
    private final SvnFileSystemListener myDelegate;

    public MyCommandListener(final SvnFileSystemListener delegate) {
      myDelegate = delegate;
    }

    public void start(final Project project) {
      if (!myInCommand && project != null) {
        myDelegate.commandStarted(project);
      }
    }

    public void finish(final Project project) {
      if (! myInCommand && project != null) {
        myDelegate.commandFinished(project);
      }
    }

    public void commandStarted(CommandEvent event) {
      myInCommand = true;
      myDelegate.commandStarted(event);
    }

    public void commandFinished(CommandEvent event) {
      myInCommand = false;
      myDelegate.commandFinished(event);
    }
  }

  private static class MyStorage implements InvocationHandler {
    private final MyCommandListener myListener;
    private final Map<Project, Pair<String, Object[]>> myStarted;

    private MyStorage(final MyCommandListener listener) {
      myListener = listener;
      myStarted = ContainerUtil.newHashMap();
    }

    @Nullable
    private static Project getProject(Object[] args) {
      for (Object arg : args) {
        if (arg instanceof VirtualFile) {
          return ProjectLocator.getInstance().guessProjectForFile((VirtualFile) arg);
        }
      }
      return null;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      final Project project = getProject(args);
      if (project != null) {
        final Pair<String, Object[]> pair = myStarted.get(project);
        if (pair != null && method.getName().equals(pair.getFirst()) && Arrays.equals(args, pair.getSecond())) {
          myListener.finish(project);
        }
      }
      //dont return null for auto unboxing to not face NPE 
      return "boolean".equals(method.getReturnType().getName()) ? Boolean.TRUE : null;
    }

    private void register(final Method method, final Object[] args) {
      final Object[] newArr = new Object[args.length];
      System.arraycopy(args, 0, newArr, 0, args.length);
      final Project project = getProject(args);
      if (project != null) {
        myListener.start(project);
        myStarted.put(project, Pair.create(method.getName(), newArr));
        Disposer.register(project, new Disposable() {
          public void dispose() {
            myStarted.remove(project);
          }
        });
      }
    }
  }

  private static class MyInvoker implements InvocationHandler {
    private final Object myDelegate;
    private final MyStorage myParent;
    private final LocalFileOperationsHandler myParentProxy;

    private MyInvoker(final MyStorage parent, Object delegate) {
      myParent = parent;
      myDelegate = delegate;
      myParentProxy = (LocalFileOperationsHandler) Proxy.newProxyInstance(LocalFileOperationsHandler.class.getClassLoader(), 
                                             new Class<?>[]{LocalFileOperationsHandler.class}, myParent);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("afterDone".equals(method.getName()) && args.length == 1) {
        ((ThrowableConsumer<LocalFileOperationsHandler, IOException>)args[0]).consume(myParentProxy);
        return null;
      }

      if (LocalFileOperationsHandler.class.equals(method.getDeclaringClass())) {
        myParent.register(method, args);
      }
      if ("equals".equals(method.getName())) {
        return args[0].equals(this);
      }
      else if ("hashCode".equals(method.getName())) {
        return 1;
      }
      return method.invoke(myDelegate, args);
    }
  }
}
