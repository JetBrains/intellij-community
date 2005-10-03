package com.intellij.rt.ant.execution;

import java.lang.reflect.InvocationTargetException;

public final class AntMain2 {
  public static final int MSG_VERBOSE = 3;
  public static final int MSG_ERR = 0;
  public static final int MSG_WARN = 1;

  public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    IdeaAntLogger2.guardStreams();

    // first try to use the new way of launching ant
    try {
      final Class antLauncher = Class.forName("org.apache.tools.ant.launch.Launcher");
      //noinspection HardCodedStringLiteral
      antLauncher.getMethod("main", new Class[]{args.getClass()}).invoke(null, new Object[]{args});
      return;
    }
    catch (ClassNotFoundException e) {
      // ignore and try older variant
    }

    final Class antMain = Class.forName("org.apache.tools.ant.Main");
    //noinspection HardCodedStringLiteral
    antMain.getMethod("main", new Class[]{args.getClass()}).invoke(null, new Object[]{args});
  }
}
