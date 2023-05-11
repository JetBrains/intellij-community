package com.jetbrains.performancePlugin.commands;

import com.intellij.platform.diagnostic.telemetry.impl.TraceUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.net.URLClassLoader;

public class RunServiceInPlugin extends RunClassInPlugin {

  public static final String PREFIX = CMD_PREFIX + "runServiceInPlugin";

  public static final String SPAN_NAME = "runServiceInPlugin";

  public RunServiceInPlugin(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected void runWithClassLoader(@NotNull Project project, URLClassLoader classLoader) throws ReflectiveOperationException {
    ClassLoaderUtil.runWithClassLoader(classLoader, () -> {
      Class<?> aClass = classLoader.loadClass(myClazzName);
      Object service = getService(project, aClass);
      if (service == null) {
        throw new RuntimeException("Cannot find an instance of class " + myClazzName + " and cannot instantiate it with Project");
      }
      TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, SPAN_NAME, globalSpan -> {
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
