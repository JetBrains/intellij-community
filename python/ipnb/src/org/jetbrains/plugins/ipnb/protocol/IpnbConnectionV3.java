// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.protocol;

import com.google.gson.Gson;
import com.intellij.openapi.project.Project;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

public class IpnbConnectionV3 extends IpnbConnection {
  private WebSocketClient myChannelsClient;
  private Thread myChannelsThread;

  public IpnbConnectionV3(@NotNull String uri,
                          @NotNull IpnbConnectionListener listener,
                          @Nullable final String token,
                          @NotNull Project project, String pathToFile) throws IOException, URISyntaxException {
    super(uri, listener, token, project, pathToFile);
  }

  @Override
  protected void initializeClients() throws URISyntaxException {
    final Draft draft = new Draft17WithOrigin();

    myChannelsClient = new IpnbWebSocketClient(getChannelsURI(), draft);
    myChannelsThread = new Thread(myChannelsClient, "IPNB channel client");
    myChannelsThread.start();
  }

  @Override
  protected void notifyOpen() {
    if (!myIsOpened) {
      myIsOpened = true;
      myListener.onOpen(this);
    }
  }

  @Override
  @NotNull
  public String execute(@NotNull String code) {
    final String messageId = UUID.randomUUID().toString();
    myChannelsClient.send(new Gson().toJson(createExecuteRequest(code, messageId)));
    return messageId;
  }

  @Override
  public void shutdown() {
    myChannelsClient.close();
  }

  @Override
  public void close() throws IOException, InterruptedException {
    myChannelsThread.join();
    shutdownKernel();
  }

  @NotNull
  public URI getChannelsURI() throws URISyntaxException {
    return new URI(getWebSocketURIBase() + "/channels");
  }

  @Override
  public boolean isAlive() {
    return myChannelsClient.isOpen();
  }
}
