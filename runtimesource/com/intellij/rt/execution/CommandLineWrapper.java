/*
 * User: anna
 * Date: 12-Aug-2008
 */
package com.intellij.rt.execution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class CommandLineWrapper {

  public static void main(String[] args)
    throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {

    final List urls = new ArrayList();
    final File file = new File(args[0]);
    final BufferedReader reader = new BufferedReader(new FileReader(file));
    try {
      while(reader.ready()) {
        urls.add(new File(reader.readLine()).toURI().toURL());
      }
    }
    finally {
      reader.close();
    }
    file.delete();
    String progClass = args[1];
    String[] progArgs = new String[args.length - 2];
    System.arraycopy(args, 2, progArgs, 0, progArgs.length);
    final URLClassLoader loader = new URLClassLoader((URL[])urls.toArray(new URL[urls.size()]), null);
    Class mainClass = loader.loadClass(progClass);
    Thread.currentThread().setContextClassLoader(loader);
    Class mainArgType = (new String[0]).getClass();
    Method main = mainClass.getMethod("main", new Class[]{mainArgType});
    Object[] argsArray = {progArgs};
    main.invoke(null, argsArray);
  }
}