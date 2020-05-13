// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.snapShooter;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SnapShotClient {
  private static final Logger LOG = Logger.getInstance(SnapShotClient.class);

  private Socket mySocket;
  @NonNls private BufferedReader myReader;
  @NonNls private OutputStreamWriter myWriter;
  private boolean myDisconnected;
  @NonNls private static final String FORM_POSTFIX = "</form>";

  public void connect(int port) throws IOException {
    mySocket = new Socket(InetAddress.getLocalHost(), port);
    mySocket.setSoTimeout(10000);
    myReader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), StandardCharsets.UTF_8));
    myWriter = new OutputStreamWriter(mySocket.getOutputStream(), StandardCharsets.UTF_8);
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
    List<SnapShotRemoteComponent> result = new ArrayList<>();
    myWriter.write("L" + id + "\n");
    myWriter.flush();
    while(true) {
      String line = myReader.readLine();
      if (line == null) {
        throw new IOException("SnapShooter disconnected");
      }
      if (line.trim().equals(".")) {
        break;
      }
      result.add(new SnapShotRemoteComponent(line, id == 0));
    }
    return result.toArray(new SnapShotRemoteComponent[0]);
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
      result.append(line).append("\n");
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
