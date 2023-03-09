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

  private final String myPluginId;
  private final String myClazzName;
  private final String myMethodName;
  private final List<File> myClasspath;

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
    URL[] cp = myClasspath.stream().map(f -> {
      try {
        return f.toURI().toURL();
      }
      catch (MalformedURLException e) {
        throw new RuntimeException("Failed to get URL for " + f + ". " + e.getMessage(), e);
      }
    }).toArray(sz -> new URL[sz]);

    URLClassLoader classLoader = new URLClassLoader(cp, loader);
    ClassLoaderUtil.runWithClassLoader(classLoader, () -> {
      Class<?> aClass = classLoader.loadClass(myClazzName);
      Object newInstance = aClass.getDeclaredConstructor().newInstance();

      try {
        Method method = aClass.getMethod(myMethodName, Project.class);
        method.invoke(newInstance, project);
        return;
      }
      catch (NoSuchMethodException ignored) { }

      try {
        Method method = aClass.getMethod(myMethodName);
        method.invoke(newInstance);
        return;
      }
      catch (NoSuchMethodException ignored) { }

      throw new RuntimeException("Class " + myClazzName + " does not have " + myMethodName + " with with no or Project parameter");
    });
  }
}
