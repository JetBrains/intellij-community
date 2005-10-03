package com.intellij.rt.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Vector;

/**
 * MUST BE COMPILED WITH JDK 1.1 IN ORDER TO SUPPORT JAVAC LAUNCHING FOR ALL JDKs
 */
public class JavacRunner {

  public static final String MSG_PATTERNS_START = "__patterns_start";
  public static final String MSG_PATTERNS_END = "__patterns_end";
  public static final String MSG_PARSING_STARTED = "MSG_PARSING_STARTED";
  public static final String MSG_PARSING_COMPLETED = "MSG_PARSING_COMPLETED";
  public static final String MSG_LOADING = "MSG_LOADING";
  public static final String MSG_CHECKING = "MSG_CHECKING";
  public static final String MSG_WROTE = "MSG_WROTE";
  public static final String MSG_WARNING = "MSG_WARNING";
  public static final String MSG_STATISTICS = "MSG_STATISTICS";

  private static final String[] BUNDLE_NAMES = new String[] {
    "com.sun.tools.javac.resources.compiler",    // v1.5
    "com.sun.tools.javac.v8.resources.compiler", // v1.3-1.4
    "sun.tools.javac.resources.javac"            // v1.1-1.2
  };

  private static final BundleKey[] MSG_NAME_KEY_PAIRS = new BundleKey[] {
    new BundleKey(MSG_PARSING_STARTED, "compiler.misc.verbose.parsing.started"),
    new BundleKey(MSG_PARSING_COMPLETED, "compiler.misc.verbose.parsing.done"),
    new BundleKey(MSG_PARSING_COMPLETED, "benv.parsed_in"), // jdk 1.1-1.2
    new BundleKey(MSG_LOADING, "compiler.misc.verbose.loading"),
    new BundleKey(MSG_LOADING, "benv.loaded_in"), // jdk 1.1-1.2
    new BundleKey(MSG_CHECKING, "compiler.misc.verbose.checking.attribution"),
    new BundleKey(MSG_WROTE,"compiler.misc.verbose.wrote.file"),
    new BundleKey(MSG_WROTE,"main.wrote"), // jdk 1.1-1.2
    new BundleKey(MSG_WARNING,"compiler.warn.warning"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.error"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.error.plural"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.warn"),
    new BundleKey(MSG_STATISTICS,"compiler.misc.count.warn.plural"),
    new BundleKey(MSG_STATISTICS,"main.errors"), //jdk 1.1 - 1.2
    new BundleKey(MSG_STATISTICS,"main.warnings"), //jdk 1.1 - 1.2
    new BundleKey(MSG_STATISTICS,"main.1error"), //jdk 1.1 - 1.2
    new BundleKey(MSG_STATISTICS,"main.1warning"), //jdk 1.1 - 1.2
  };
  public static final String CATEGORY_VALUE_DIVIDER = "=";

  /**
   * @param args - params
   *  0. jdk version string
   *  1. javac main class
   *  2. javac parameters
   */
  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException {

    if (!processCompilerResourceBundle()) {
      return;
    }

    final String versionString = args[0];
    final Class aClass = Class.forName(args[1]);
    //noinspection HardCodedStringLiteral
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
      //noinspection HardCodedStringLiteral
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


  private static boolean processCompilerResourceBundle() {
    final ResourceBundle messagesBundle = getMessagesBundle();
    if (messagesBundle == null) {
      return false;
    }
    System.err.println(MSG_PATTERNS_START);
    for (int idx = 0; idx < MSG_NAME_KEY_PAIRS.length; idx++) {
      BundleKey bundleKey = MSG_NAME_KEY_PAIRS[idx];
      try {
        System.err.println(bundleKey.category + CATEGORY_VALUE_DIVIDER + messagesBundle.getString(bundleKey.key));
      }
      catch (MissingResourceException ignored) {
      }
    }
    System.err.println(MSG_PATTERNS_END);
    return true;
  }

  private static ResourceBundle getMessagesBundle() {
    for (int i = 0; i < BUNDLE_NAMES.length; i++) {
      try {
        return ResourceBundle.getBundle(BUNDLE_NAMES[i]);
      }
      catch (MissingResourceException ignored) {
        continue;
      }
    }
    return null;
  }

  private static final class BundleKey {
    public final String category;
    public final String key;

    public BundleKey(final String category, final String key) {
      this.category = category;
      this.key = key;
    }
  }

}
