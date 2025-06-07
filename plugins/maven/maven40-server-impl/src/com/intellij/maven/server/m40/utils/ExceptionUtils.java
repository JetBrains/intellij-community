// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;

public final class ExceptionUtils {
  static final String WRAPPED_MARKER = " [wrapped] ";

  private static String[] CAUSE_METHOD_NAMES = {
    "getCause",
    "getNextException",
    "getTargetException",
    "getException",
    "getSourceException",
    "getRootCause",
    "getCausedByException",
    "getNested"
  };

  private ExceptionUtils() {}

  public static void addCauseMethodName(String methodName) {
    if (methodName != null && !methodName.isEmpty()) {
      List<String> list = new ArrayList<String>(Arrays.asList(CAUSE_METHOD_NAMES));
      list.add(methodName);
      CAUSE_METHOD_NAMES = list.toArray(new String[0]);
    }
  }

  public static Throwable getCause(Throwable throwable) {
    return getCause(throwable, CAUSE_METHOD_NAMES);
  }

  public static Throwable getCause(Throwable throwable, String[] methodNames) {
    Throwable cause = getCauseUsingWellKnownTypes(throwable);
    if (cause == null) {
      for (String methodName : methodNames) {
        cause = getCauseUsingMethodName(throwable, methodName);
        if (cause != null) {
          break;
        }
      }

      if (cause == null) {
        cause = getCauseUsingFieldName(throwable, "detail");
      }
    }
    return cause;
  }

  public static Throwable getRootCause(Throwable throwable) {
    Throwable cause = getCause(throwable);
    if (cause != null) {
      throwable = cause;
      while ((throwable = getCause(throwable)) != null) {
        cause = throwable;
      }
    }
    return cause;
  }

  private static Throwable getCauseUsingWellKnownTypes(Throwable throwable) {
    if (throwable instanceof SQLException) {
      return ((SQLException) throwable).getNextException();
    } else if (throwable instanceof InvocationTargetException) {
      return ((InvocationTargetException) throwable).getTargetException();
    } else {
      return null;
    }
  }

  private static Throwable getCauseUsingMethodName(Throwable throwable, String methodName) {
    Method method = null;
    try {
      method = throwable.getClass().getMethod(methodName, null);
    } catch (NoSuchMethodException | SecurityException ignored) {
    }

    if (method != null && Throwable.class.isAssignableFrom(method.getReturnType())) {
      try {
        return (Throwable) method.invoke(throwable, new Object[0]);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ignored) {
      }
    }
    return null;
  }

  private static Throwable getCauseUsingFieldName(Throwable throwable, String fieldName) {
    Field field = null;
    try {
      field = throwable.getClass().getField(fieldName);
    } catch (NoSuchFieldException | SecurityException ignored) {
    }

    if (field != null && Throwable.class.isAssignableFrom(field.getType())) {
      try {
        return (Throwable) field.get(throwable);
      } catch (IllegalAccessException | IllegalArgumentException ignored) {
      }
    }
    return null;
  }

  public static int getThrowableCount(Throwable throwable) {
    // Count the number of throwables
    int count = 0;
    while (throwable != null) {
      count++;
      throwable = ExceptionUtils.getCause(throwable);
    }
    return count;
  }

  public static Throwable[] getThrowables(Throwable throwable) {
    List<Throwable> list = new ArrayList<>();
    while (throwable != null) {
      list.add(throwable);
      throwable = getCause(throwable);
    }
    return list.toArray(new Throwable[0]);
  }

  public static int indexOfThrowable(Throwable throwable, Class type) {
    return indexOfThrowable(throwable, type, 0);
  }

  public static int indexOfThrowable(Throwable throwable, Class type, int fromIndex) {
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("Throwable index out of range: " + fromIndex);
    }
    Throwable[] throwables = ExceptionUtils.getThrowables(throwable);
    if (fromIndex >= throwables.length) {
      throw new IndexOutOfBoundsException("Throwable index out of range: " + fromIndex);
    }
    for (int i = fromIndex; i < throwables.length; i++) {
      if (throwables[i].getClass().equals(type)) {
        return i;
      }
    }
    return -1;
  }

  public static void printRootCauseStackTrace(Throwable t, PrintStream stream) {
    String[] trace = getRootCauseStackTrace(t);
    for (String aTrace : trace) {
      stream.println(aTrace);
    }
    stream.flush();
  }

  public static void printRootCauseStackTrace(Throwable t) {
    printRootCauseStackTrace(t, System.err);
  }

  public static void printRootCauseStackTrace(Throwable t, PrintWriter writer) {
    String[] trace = getRootCauseStackTrace(t);
    for (String aTrace : trace) {
      writer.println(aTrace);
    }
    writer.flush();
  }

  public static String[] getRootCauseStackTrace(Throwable t) {
    Throwable[] throwables = getThrowables(t);
    int count = throwables.length;
    ArrayList<String> frames = new ArrayList<>();
    List<String> nextTrace = getStackFrameList(throwables[count - 1]);
    for (int i = count; --i >= 0; ) {
      List<String> trace = nextTrace;
      if (i != 0) {
        nextTrace = getStackFrameList(throwables[i - 1]);
        removeCommonFrames(trace, nextTrace);
      }
      if (i == (count - 1)) {
        frames.add(throwables[i].toString());
      } else {
        frames.add(WRAPPED_MARKER + throwables[i].toString());
      }
      frames.addAll(trace);
    }
    return frames.toArray(new String[0]);
  }

  private static void removeCommonFrames(List<String> causeFrames, List<String> wrapperFrames) {
    int causeFrameIndex = causeFrames.size() - 1;
    int wrapperFrameIndex = wrapperFrames.size() - 1;
    while (causeFrameIndex >= 0 && wrapperFrameIndex >= 0) {
      // Remove the frame from the cause trace if it is the same
      // as in the wrapper trace
      String causeFrame = causeFrames.get(causeFrameIndex);
      String wrapperFrame = wrapperFrames.get(wrapperFrameIndex);
      if (causeFrame.equals(wrapperFrame)) {
        causeFrames.remove(causeFrameIndex);
      }
      causeFrameIndex--;
      wrapperFrameIndex--;
    }
  }

  public static String getStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);
    t.printStackTrace(pw);
    return sw.getBuffer().toString();
  }

  public static String getFullStackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);
    Throwable[] ts = getThrowables(t);
    for (Throwable t1 : ts) {
      t1.printStackTrace(pw);
      if (isNestedThrowable(t1)) {
        break;
      }
    }
    return sw.getBuffer().toString();
  }

  public static boolean isNestedThrowable(Throwable throwable) {
    if (throwable == null) {
      return false;
    }

    if (throwable instanceof SQLException) {
      return true;
    } else if (throwable instanceof InvocationTargetException) {
      return true;
    }

    for (String CAUSE_METHOD_NAME : CAUSE_METHOD_NAMES) {
      try {
        Method method = throwable.getClass().getMethod(CAUSE_METHOD_NAME, null);
        return true;
      } catch (NoSuchMethodException | SecurityException ignored) {
      }
    }

    try {
      Field field = throwable.getClass().getField("detail");
      return true;
    } catch (NoSuchFieldException | SecurityException ignored) {
    }

    return false;
  }

  public static String[] getStackFrames(Throwable t) {
    return getStackFrames(getStackTrace(t));
  }

  static String[] getStackFrames(String stackTrace) {
    String linebreak = System.getProperty("line.separator");
    StringTokenizer frames = new StringTokenizer(stackTrace, linebreak);
    List<String> list = new LinkedList<String>();
    while (frames.hasMoreTokens()) {
      list.add(frames.nextToken());
    }
    return list.toArray(new String[0]);
  }

  static List<String> getStackFrameList(Throwable t) {
    String stackTrace = getStackTrace(t);
    String linebreak = System.getProperty("line.separator");
    StringTokenizer frames = new StringTokenizer(stackTrace, linebreak);
    List<String> list = new LinkedList<String>();
    boolean traceStarted = false;
    while (frames.hasMoreTokens()) {
      String token = frames.nextToken();
      // Determine if the line starts with <whitespace>at
      int at = token.indexOf("at");
      if (at != -1 && token.substring(0, at).trim().isEmpty()) {
        traceStarted = true;
        list.add(token);
      } else if (traceStarted) {
        break;
      }
    }
    return list;
  }
}
