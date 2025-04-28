// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginContentDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.net.URLClassLoader;
import java.util.List;

public class RunServiceInPlugin extends RunClassInPlugin {

  public static final String PREFIX = CMD_PREFIX + "runServiceInPlugin";

  public static final String SPAN_NAME = "runServiceInPlugin";

  public RunServiceInPlugin(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public void computePromise(@NotNull Project project) throws Exception {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(myPluginId));
    if (plugin == null) throw new RuntimeException("Failed to find plugin: " + myPluginId);

    ClassLoader loader = null;
    // requires to avoid "class must not be requested from main classloader of plugin" error
    List<PluginContentDescriptor.ModuleItem> modules = ((IdeaPluginDescriptorImpl)plugin).getContent().modules;
    if (!modules.isEmpty()) {
      for (PluginContentDescriptor.ModuleItem module : modules) {
        if (myClazzName.contains(module.getName())) {
          loader = module.requireDescriptor().getClassLoader();
        }
      }
    }

    if (loader == null) {
      loader = plugin.getClassLoader();
    }

    URLClassLoader classLoader = new URLClassLoader(convertClasspathToURLs(), loader);
    runWithClassLoader(project, classLoader);
  }

  @Override
  protected void runWithClassLoader(@NotNull Project project, URLClassLoader classLoader) throws ReflectiveOperationException {
    ClassLoaderUtil.runWithClassLoader(classLoader, () -> {
      Class<?> aClass = classLoader.loadClass(myClazzName);
      Object service = getService(project, aClass);
      if (service == null) {
        throw new RuntimeException("Cannot find an instance of class " + myClazzName + " and cannot instantiate it with Project");
      }
      TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME), __ -> {
          invokeMethod(project, aClass, service);
        });
    });
  }

  private static Object getService(@NotNull Project project, Class<?> aClass) throws ReflectiveOperationException {
    // the difference with the parent is that service already exists and initialized
    Object service = project.getService(aClass);
    if (service == null) {
      // fallback in case service is not initialized
      try {
        Constructor<?> aClassConstructor = aClass.getConstructor(Project.class);
        service = aClassConstructor.newInstance(project);
      }
      catch (NoSuchMethodException ignored) {
      }
    }
    return service;
  }
}
