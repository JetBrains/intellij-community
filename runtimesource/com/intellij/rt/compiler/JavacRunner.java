package com.intellij.rt.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Vector;

/**
 * MUST BE COMPILED WITH JDK 1.1 IN ORDER TO SUPPORT JAVAC LAUNCHING FOR ALL JDKs
 */
public class JavacRunner {
  /**
   * @param args - params
   *  0. jdk version string
   *  1. javac main class
   *  2. javac parameters
   */
  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException {

    Locale.setDefault(Locale.US);

    final String versionString = args[0];
    final Class aClass = Class.forName(args[1]);
    final Method mainMethod = aClass.getMethod("main", new Class[] {String[].class});
    String[] newArgs;
    if (versionString.indexOf("1.1") > -1) {
      // expand the file
      Vector arguments = new Vector();
      for (int idx = 3; idx < args.length; idx++) {
        String arg = args[idx];
        if (arg.startsWith("@")) {
          String path = arg.substring(1);
          addFilesToCompile(arguments, path);
        }
        else {
          arguments.addElement(arg);
        }
      }
      newArgs = new String[arguments.size()];
      for (int idx = 0; idx < newArgs.length; idx++) {
        newArgs[idx] = (String)arguments.elementAt(idx);
      }
    }
    else {
      newArgs = new String[args.length - 2];
      System.arraycopy(args, 2, newArgs, 0, newArgs.length);
    }
    expandClasspath(newArgs);
    mainMethod.invoke(null, new Object[] {newArgs});
  }

  private static void addFilesToCompile(Vector arguments, String path) throws IOException {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(new File(path)));
      for (String filePath = reader.readLine(); filePath != null; filePath = reader.readLine()) {
        arguments.addElement(filePath.replace('/', File.separatorChar));
      }
    }
    finally {
      if (reader != null) {
        reader.close();
      }
    }
  }

  private static void expandClasspath(String[] args) throws IOException {
    for (int idx = 0; idx < args.length; idx++) {
      final String arg = args[idx];
      if ("-classpath".equals(arg) || "-cp".equals(arg)) {
        final String cpValue = args[idx + 1];
        if (cpValue.startsWith("@")) {
          args[idx + 1] = readClasspath(cpValue.substring(1));
        }
        break;
      }
    }
  }

  public static String readClasspath(String filePath) throws IOException {
    BufferedReader reader = null;
    final StringBuffer buf = new StringBuffer();
    try {
      reader = new BufferedReader(new FileReader(new File(filePath)));
      for (String path = reader.readLine(); path != null; path = reader.readLine()) {
        if (buf.length() > 0) {
          buf.append(File.pathSeparator);
        }
        buf.append(path);
      }
    }
    finally {
      if (reader != null) {
        reader.close();
      }
    }
    return buf.toString();
  }

}
