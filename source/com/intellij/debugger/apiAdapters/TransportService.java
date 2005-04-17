package com.intellij.debugger.apiAdapters;

import com.intellij.util.ArrayUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.execution.ExecutionException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author max
 */
public class TransportService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.apiAdapters.TransportService");

  private final Object myDelegateObject;
  private final Class myDelegateClass;
  private static final String SOCKET_TRANSPORT_CLASS = SystemInfo.JAVA_VERSION.startsWith("1.4")
                                                       ? "com.sun.tools.jdi.SocketTransport"
                                                       : "com.sun.tools.jdi.SocketTransportService";
  private static final String SHMEM_TRANSPORT_CLASS = SystemInfo.JAVA_VERSION.startsWith("1.4")
                                                      ? "com.sun.tools.jdi.SharedMemoryTransport"
                                                      : "com.sun.tools.jdi.SharedMemoryTransportService";

  private TransportService(Class delegateClass) throws NoSuchMethodException,
                                                      IllegalAccessException,
                                                      InvocationTargetException,
                                                      InstantiationException {
    myDelegateClass = delegateClass;
    final Constructor constructor = delegateClass.getDeclaredConstructor(new Class[0]);
    constructor.setAccessible(true);
    myDelegateObject = constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public ConnectionService attach(final String s) {
    try {
      final Method method = myDelegateClass.getMethod("attach", new Class[]{String.class});
      return new ConnectionService(method.invoke(myDelegateObject, new Object[]{s}));
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    return null;
  }

  public String startListening() throws IOException {
    try {
      final Method method = myDelegateClass.getMethod("startListening", new Class[0]);
      return (String)method.invoke(myDelegateObject, new Object[0]);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    return null;
  }

  public void stopListening(final String address) throws IOException {
    try {
      final Method method = myDelegateClass.getMethod("stopListening", new Class[] {String.class});
      method.invoke(myDelegateObject, new Object[]{address});
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
  }

  public String transportId() {
    if (SOCKET_TRANSPORT_CLASS.equals(myDelegateClass.getName())) {
      return "dt_socket";
    }
    else if (SHMEM_TRANSPORT_CLASS.equals(myDelegateClass.getName())) {
      return "dt_shmem";
    }

    LOG.error("Unknown serivce");
    return "<unknown>";
  }

  public static TransportService getTransportService(boolean forceSocketTransport) throws ExecutionException {
    TransportService transport;
    try {
      try {
        if (forceSocketTransport) {
          transport = new TransportService(Class.forName(SOCKET_TRANSPORT_CLASS));
        }
        else {
          transport = new TransportService(Class.forName(SHMEM_TRANSPORT_CLASS));
        }
      }
      catch (UnsatisfiedLinkError e) {
        transport = new TransportService(Class.forName(SOCKET_TRANSPORT_CLASS));
      }
    }
    catch (Exception e) {
      throw new ExecutionException(e.getClass().getName() + " : " + e.getMessage());
    }
    return transport;
  }
}
