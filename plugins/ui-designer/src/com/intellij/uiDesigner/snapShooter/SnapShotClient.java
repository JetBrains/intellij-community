/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NonNls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

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
    mySocket.setSoTimeout(10000);
    myReader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), CharsetToolkit.UTF8_CHARSET));
    myWriter = new OutputStreamWriter(mySocket.getOutputStream(), CharsetToolkit.UTF8_CHARSET);
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
      result.add(new SnapShotRemoteComponent(line, id == 0));
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
