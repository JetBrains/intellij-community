package com.intellij.execution.runners;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author ven
 */

class ProcessProxyImpl implements ProcessProxy {
  public static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");
  private int myPortNumber;

  private static final int SOCKET_NUMBER_START = 7532;
  private static final int SOCKET_NUMBER = 100;
  private static final boolean[] ourUsedSockets = new boolean[SOCKET_NUMBER];

  private PrintWriter myWriter;
  private Socket mySocket;
  private static final String DONT_USE_LAUNCHER_PROPERTY = "idea.no.launcher";
  public static final String PROPERTY_BINPATH = "idea.launcher.bin.path";
  public static final String PROPERTY_PORT_NUMBER = "idea.launcher.port";
  public static final String LAUNCH_MAIN_CLASS = "com.intellij.rt.execution.application.AppMain";

  public int getPortNumber() {
    return myPortNumber;
  }

  public static class NoMoreSocketsException extends Exception {
  }

  public ProcessProxyImpl () throws NoMoreSocketsException {
    ServerSocket s;
    synchronized (ourUsedSockets) {
      for (int j = 0; j < SOCKET_NUMBER; j++) {
        if (ourUsedSockets[j]) continue;
        try {
          s = new ServerSocket(j + SOCKET_NUMBER_START);
          s.close();
          myPortNumber = j + SOCKET_NUMBER_START;
          ourUsedSockets[j] = true;

          return;
        } catch (IOException e) {
          continue;
        }
      }
    }
    throw new NoMoreSocketsException();
  }

  public void finalize () throws Throwable {
    if (myWriter != null) {
      myWriter.close();
    }
    ourUsedSockets[myPortNumber - SOCKET_NUMBER_START] = false;
    super.finalize();
  }

  public void attach(final ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
  }

  private synchronized void writeLine (final String s) {
    if (myWriter == null) {
      try {
        if (mySocket == null) mySocket = new Socket(InetAddress.getByName("localhost"), myPortNumber);
        myWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mySocket.getOutputStream())));
      } catch (IOException e) {
        return;
      }
    }
    myWriter.println(s);
    myWriter.flush();
  }

  public void sendBreak () {
    writeLine("BREAK");
  }

  public void sendStop () {
    writeLine("STOP");
  }

  public static boolean useLauncher() {
    if ("true".equals(System.getProperty(DONT_USE_LAUNCHER_PROPERTY))) {
      return false;
    }

    if (!SystemInfo.isWindows && !SystemInfo.isLinux) {
      return false;
    }
    return new File(getLaunchertLibName()).exists();
  }

  public static String getLaunchertLibName() {
    final String libName = SystemInfo.isWindows ? "breakgen.dll" : "libbreakgen.so";
    return PathManager.getBinPath() + File.separator + libName;
  }
}
