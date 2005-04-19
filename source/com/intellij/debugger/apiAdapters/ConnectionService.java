package com.intellij.debugger.apiAdapters;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * @author max
 */
public class ConnectionService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.apiAdapters.ConnectionService");

  private static Class myDelegateClass;
  private Object myConnection;

  static {
    try {
      myDelegateClass = SystemInfo.JAVA_VERSION.startsWith("1.4")
                        ? Class.forName("com.sun.tools.jdi.ConnectionService")
                        : Class.forName("com.sun.jdi.connect.spi.Connection");
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

  public ConnectionService(final Object connection) {
    myConnection = connection;
  }

  public void close() throws IOException {
    try {
      final Method method = myDelegateClass.getMethod("close", new Class[0]);
      method.invoke(myConnection, new Object[0]);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      LOG.error(e);
    }
  }

  public VirtualMachine createVirtualMachine() throws IOException {
    try {
      final VirtualMachineManager virtualMachineManager = Bootstrap.virtualMachineManager();
      final Method method = virtualMachineManager.getClass().getMethod("createVirtualMachine", new Class[]{myDelegateClass});
      return (VirtualMachine)method.invoke(virtualMachineManager, new Object[]{myConnection});
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      LOG.error(e);
    }
    return null;
  }
}
