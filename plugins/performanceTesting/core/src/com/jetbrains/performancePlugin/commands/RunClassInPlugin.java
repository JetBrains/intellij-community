// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RunClassInPlugin extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "runClassInPlugin";
  protected final String myPluginId;
  protected final String myClazzName;
  protected final String myMethodName;
  protected final List<File> myClasspath;

  private static String nextArg(Iterator<String> args, @NotNull String text) {
    if (!args.hasNext()) throw new RuntimeException("Too few arguments in " + text);
    return args.next();
  }

  public RunClassInPlugin(@NotNull String text, int line) {
    super(text, line);

    Iterator<String> args = StringUtil.splitHonorQuotes(text, ' ').stream().map(StringUtil::unquoteString).iterator();

    //the command name
    nextArg(args, text);

    myPluginId = nextArg(args, text);
    myClazzName = nextArg(args, text);
    myMethodName = nextArg(args, text);
    List<File> classpath = new ArrayList<>();
    args.forEachRemaining(arg -> classpath.add(new File(arg)));
    myClasspath = Collections.unmodifiableList(classpath);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    AsyncPromise<Object> promise = new AsyncPromise<>();

    AppExecutorUtil.getAppExecutorService().execute(() -> {
      try {
        computePromise(context.getProject());
        promise.setResult("completed");
      } catch (Throwable t) {
        promise.setError(t);
      }
    });

    return promise;
  }

  public void computePromise(@NotNull Project project) throws Exception {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(myPluginId));
    if (plugin == null) throw new RuntimeException("Failed to find plugin: " + myPluginId);

    ClassLoader loader = plugin.getClassLoader();

    URLClassLoader classLoader = new URLClassLoader(convertClasspathToURLs(), loader);
    runWithClassLoader(project, classLoader);
  }

  public URL[] convertClasspathToURLs() {
    return myClasspath.stream().map(f -> {
      try {
        URL fileURL = f.toURI().toURL();
        if (!fileURL.getProtocol().equals("file")) {
          throw new RuntimeException("Remote resources are not allowed in the classpath: " + fileURL);
        }
        return fileURL;
      }
      catch (MalformedURLException e) {
        throw new RuntimeException("Failed to get URL for " + f + ". " + e.getMessage(), e);
      }
    }).toArray(sz -> new URL[sz]);
  }

  protected void runWithClassLoader(@NotNull Project project, URLClassLoader classLoader) throws ReflectiveOperationException {
    ClassLoaderUtil.runWithClassLoader(classLoader, () -> {
      Class<?> aClass = classLoader.loadClass(myClazzName);
      Object newInstance = aClass.getDeclaredConstructor().newInstance();
      invokeMethod(project, aClass, newInstance);
    });
  }

  protected void invokeMethod(@NotNull Project project,
                              @NotNull Class<?> aClass,
                              Object instance) throws IllegalAccessException, InvocationTargetException {
    try {
      Method method = aClass.getMethod(myMethodName, Project.class);
      method.invoke(instance, project);
      return;
    }
    catch (NoSuchMethodException ignored) {
    }
    catch (InvocationTargetException e) {
      rethrowInvocationTargetExceptionCauseIfCan(e);
    }

    try {
      Method method = aClass.getMethod(myMethodName);
      method.invoke(instance);
      return;
    }
    catch (NoSuchMethodException ignored) {
    }
    catch (InvocationTargetException e) {
      rethrowInvocationTargetExceptionCauseIfCan(e);
    }

    throw new RuntimeException("Class " + myClazzName + " does not have " + myMethodName + " with no or Project parameter");
  }

  private static void rethrowInvocationTargetExceptionCauseIfCan(InvocationTargetException e) throws InvocationTargetException {
    if (e.getCause() instanceof RuntimeException re) {
      throw re;
    }
    else if (e.getCause() instanceof Error err) {
      throw err;
    }
    else {
      throw e;
    }
  }
}
