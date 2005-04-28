package com.intellij.debugger.apiAdapters;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.sun.jdi.connect.Transport;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author max
 */
public class TransportServiceWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.apiAdapters.TransportService");

  private final Object myDelegateObject;
  private final Class myDelegateClass;
  private static final String SOCKET_TRANSPORT_CLASS = SystemInfo.JAVA_VERSION.startsWith("1.4")
                                                       ? "com.sun.tools.jdi.SocketTransport"
                                                       : "com.sun.tools.jdi.SocketTransportService";
  private static final String SHMEM_TRANSPORT_CLASS = SystemInfo.JAVA_VERSION.startsWith("1.4")
                                                      ? "com.sun.tools.jdi.SharedMemoryTransport"
                                                      : "com.sun.tools.jdi.SharedMemoryTransportService";

  private TransportServiceWrapper(Class delegateClass) throws NoSuchMethodException,
                                                      IllegalAccessException,
                                                      InvocationTargetException,
                                                      InstantiationException {
    myDelegateClass = delegateClass;
    final Constructor constructor = delegateClass.getDeclaredConstructor(new Class[0]);
    constructor.setAccessible(true);
    myDelegateObject = constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  /**
   * Applicable if IDEA is run on JDK 1.4.2.x only!
   * @param transportObj
   */
  private TransportServiceWrapper(Transport transportObj) {
    myDelegateClass = transportObj.getClass();
    myDelegateObject = transportObj;
  }

  public ConnectionServiceWrapper attach(final String s) throws IOException {
    try {
      // Applicable if IDEA is run on JDK 1.4.2.x only!
      // in JDK 1.5 the signature of the "attach" method has been changed to "attach(String, long, long)"
      final Method method = myDelegateClass.getMethod("attach", new Class[]{String.class});
      method.setAccessible(true);
      return new ConnectionServiceWrapper(method.invoke(myDelegateObject, new Object[]{s}));
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
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
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
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
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

  public static TransportServiceWrapper getTransportService(boolean forceSocketTransport) throws ExecutionException {
    TransportServiceWrapper transport;
    try {
      try {
        if (forceSocketTransport) {
          transport = new TransportServiceWrapper(Class.forName(SOCKET_TRANSPORT_CLASS));
        }
        else {
          transport = new TransportServiceWrapper(Class.forName(SHMEM_TRANSPORT_CLASS));
        }
      }
      catch (UnsatisfiedLinkError e) {
        transport = new TransportServiceWrapper(Class.forName(SOCKET_TRANSPORT_CLASS));
      }
    }
    catch (Exception e) {
      throw new ExecutionException(e.getClass().getName() + " : " + e.getMessage());
    }
    return transport;
  }

  /**
   * Applicable if IDEA is run on JDK 1.4.2.x only!
   * @param transportObject
   * @return transport service wrapper
   */
  public static TransportServiceWrapper getTransportService(Transport transportObject){
    return new TransportServiceWrapper(transportObject);
  }

}
