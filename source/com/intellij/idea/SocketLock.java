package com.intellij.idea;

import com.intellij.diagnostic.ReportMessages;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;

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
        JOptionPane.showMessageDialog(
          JOptionPane.getRootFrame(),
          "IDEA was unable to create a local connection in order to check whether\n" +
          "other instance of IDEA is currently running on the same machine.\n" +
          "Running multiple instances of IDEA on the same machine may cause unpredictable\n" +
          "results because of sharing system folders.\n" +
          "Please troubleshoot your TCP/IP configuration and/or local firewall settings.\n" +
           ReportMessages.getReportAddress() + "\n" +
          "and attach the " + PathManager.getSystemPath() + "/log/idea.log file".replace('/', File.separatorChar),
          "Warning",
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

      Socket socket = new Socket("localhost", i);
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

    new Thread(new MyRunnable(), "Lock thread").start();

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
