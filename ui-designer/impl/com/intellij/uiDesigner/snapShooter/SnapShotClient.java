/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class SnapShotClient {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.snapShooter.SnapShotClient");

  private Socket mySocket;
  @NonNls private BufferedReader myReader;
  @NonNls private OutputStreamWriter myWriter;
  private boolean myDisconnected;
  @NonNls private static final String FORM_POSTFIX = "</form>";

  public void connect(int port) throws IOException {
    mySocket = new Socket(InetAddress.getLocalHost(), port);
    mySocket.setSoTimeout(2000);
    myReader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
    myWriter = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
  }

  public void dispose() {
    try {
      myWriter.close();
      myReader.close();
      mySocket.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void suspendSwing() throws IOException {
    myWriter.write("S\n");
    myWriter.flush();
  }

  public void resumeSwing() throws IOException {
    myWriter.write("R\n");
    myWriter.flush();
  }

  public SnapShotRemoteComponent[] listChildren(final int id) throws IOException {
    if (myDisconnected) {
      return new SnapShotRemoteComponent[0];
    }
    List<SnapShotRemoteComponent> result = new ArrayList<SnapShotRemoteComponent>();
    myWriter.write("L" + Integer.toString(id) + "\n");
    myWriter.flush();
    while(true) {
      String line = myReader.readLine();
      if (line == null) {
        throw new IOException("SnapShooter disconnected");
      }
      if (line.trim().equals(".")) {
        break;
      }
      result.add(new SnapShotRemoteComponent(line));
    }
    return result.toArray(new SnapShotRemoteComponent[result.size()]);
  }

  public String createSnapshot(final int id) throws Exception {
    myWriter.write("X" + id + "\n");
    myWriter.flush();
    StringBuilder result = new StringBuilder();
    while(true) {
      @NonNls String line = myReader.readLine();
      if (line == null) {
        throw new IOException("SnapShooter disconnected");
      }
      if (result.length() == 0 && line.startsWith("E:")) {
        throw new Exception(line.substring(2));
      }
      result.append(line);
      if (line.trim().endsWith(FORM_POSTFIX)) {
        break;
      }
    }
    return result.toString();
  }

  public void setDisconnected() {
    myDisconnected = true;
  }

}
