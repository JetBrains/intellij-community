package com.intellij.idea;

import com.intellij.diagnostic.ReportMessages;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NonNls;

/**
 * @author mike
 */
public class SocketLock {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.SocketLock");
  private static final int SOCKET_NUMBER_START = 6942;
  private static final int SOCKET_NUMBER_END = SOCKET_NUMBER_START + 50;

  private ServerSocket mySocket;
  private List myLockedPaths = new ArrayList();
  private boolean myIsDialogShown = false;
  @NonNls private static final String LOCALHOST = "localhost";
  @NonNls private static final String LOCK_THREAD_NAME = "Lock thread";

  public SocketLock() {
  }

  public synchronized void dispose() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: destroyProcess()");
    }
    try {
      mySocket.close();
      mySocket = null;
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  public synchronized boolean lock(String path) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: lock(path='" + path + "')");
    }

    acquireSocket();
    if (mySocket == null) {
      if (!myIsDialogShown) {
        final String productName = ApplicationNamesInfo.getInstance().getProductName();
        @NonNls final String pathToLogFile = PathManager.getSystemPath() + "/log/idea.log file".replace('/', File.separatorChar);
        JOptionPane.showMessageDialog(
          JOptionPane.getRootFrame(),
          CommonBundle.message("cannot.start.other.instance.is.running.error.message", productName, pathToLogFile),
          CommonBundle.message("title.warning"),
          JOptionPane.WARNING_MESSAGE
        );
        myIsDialogShown = true;
      }
      return true;
    }

    for (int i = SOCKET_NUMBER_START; i < SOCKET_NUMBER_END; i++) {
      if (i == mySocket.getLocalPort()) continue;
      List lockedList = readLockedList(i);
      if (lockedList.contains(path)) return false;
    }

    myLockedPaths.add(path);

    return true;
  }

  public synchronized void unlock(String path) {
    myLockedPaths.remove(path);
  }

  private List readLockedList(int i) {
    List result = new ArrayList();

    try {
      try {
        ServerSocket serverSocket = new ServerSocket(i);
        serverSocket.close();
        return result;
      }
      catch (IOException e) {
      }

      Socket socket = new Socket(LOCALHOST, i);
      DataInputStream in = new DataInputStream(socket.getInputStream());

      while (true) {
        try {
          result.add(in.readUTF());
        }
        catch (IOException e) {
          break;
        }
      }

      in.close();
    }
    catch (IOException e) {
      LOG.debug(e);
    }

    return result;
  }

  private void acquireSocket() {
    if (mySocket != null) return;

    for (int i = SOCKET_NUMBER_START; i < SOCKET_NUMBER_END; i++) {
      try {
        mySocket = new ServerSocket(i);
        break;
      }
      catch (IOException e) {
        LOG.info(e);
        continue;
      }
    }

    new Thread(new MyRunnable(), LOCK_THREAD_NAME).start();

    return;
  }

  private synchronized void writeLockedPaths(Socket socket) {
    try {
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      for (Iterator iterator = myLockedPaths.iterator(); iterator.hasNext();) {
        String path = (String)iterator.next();
        out.writeUTF(path);
      }
      out.close();
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  private class MyRunnable implements Runnable {
    public void run() {
      try {
        while (true) {
          try {
            final Socket socket = mySocket.accept();
            writeLockedPaths(socket);
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
      }
      catch (Throwable e) {
      }
    }
  }
}
