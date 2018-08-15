// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.ssl.CertificateManager;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public class IpnbWebSocketClient extends WebSocketClient {
  private static final Logger LOG = Logger.getInstance(IpnbWebSocketClient.class);
  private @NotNull final IpnbConnection myConnection;

  public IpnbWebSocketClient(@NotNull final URI serverUri, @NotNull final Draft draft, @NotNull IpnbConnection connection) {
    super(serverUri, draft, connection.getHeaders(), 10000);
    myConnection = connection;
    configureSsl(serverUri);
  }

  private void configureSsl(@NotNull URI serverUri) {
    if (serverUri.getScheme().equals("wss")) {
      final SSLContext sslContext = CertificateManager.getInstance().getSslContext();
      try {
        this.setSocket(sslContext.getSocketFactory().createSocket());
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
      }
    }
  }

  @Override
  public void onOpen(ServerHandshake handshakeData) {
    final IpnbConnection.Message message = myConnection.createMessage("connect_request", UUID.randomUUID().toString(), null, null);
    send(new Gson().toJson(message));
    myConnection.setIOPubOpen(true);
    myConnection.notifyOpen();
  }

  @Override
  public void onMessage(String message) {
    final Gson gson = new Gson();
    final IpnbConnection.Message msg = gson.fromJson(message, IpnbConnection.Message.class);
    final IpnbConnection.Header header = msg.getHeader();
    final IpnbConnection.Header parentHeader = gson.fromJson(msg.getParentHeader(), IpnbConnection.Header.class);
    final String messageType = header.getMessageType();
    if ("pyout".equals(messageType) || "display_data".equals(messageType) || "execute_result".equals(messageType)) {
      final IpnbConnection.PyOutContent content = gson.fromJson(msg.getContent(), IpnbConnection.PyOutContent.class);
      myConnection.addCellOutput(content);
      myConnection.getListener().onOutput(myConnection, parentHeader.getMessageId());
    }
    if ("execute_reply".equals(messageType)) {
      final IpnbConnection.PyExecuteReplyContent content = gson.fromJson(msg.getContent(), IpnbConnection.PyExecuteReplyContent.class);
      final List<IpnbConnection.Payload> payloads = content.getPayload();
      if (payloads != null && !payloads.isEmpty()) {
        final IpnbConnection.Payload payload = payloads.get(0);
        if (payload.replace) {
          myConnection.getListener().onPayload(payload.text, parentHeader.getMessageId());
        }
      }
      if ("ok".equals(content.getStatus()) || "error".equals(content.getStatus())) {
        myConnection.getListener().onFinished(myConnection, parentHeader.getMessageId());
      }
    }
    else if ("pyerr".equals(messageType) || "error".equals(messageType)) {
      final IpnbConnection.PyErrContent content = gson.fromJson(msg.getContent(), IpnbConnection.PyErrContent.class);
      myConnection.addCellOutput(content);
      myConnection.getListener().onOutput(myConnection, parentHeader.getMessageId());
    }
    else if ("stream".equals(messageType)) {
      final IpnbConnection.PyStreamContent content = gson.fromJson(msg.getContent(), IpnbConnection.PyStreamContent.class);
      myConnection.addCellOutput(content);
      myConnection.getListener().onOutput(myConnection, parentHeader.getMessageId());
    }
    else if ("pyin".equals(messageType) || "execute_input".equals(messageType)) {
      final JsonElement executionCount = msg.getContent().get("execution_count");
      if (executionCount != null) {
        myConnection.setExecCount(executionCount.getAsInt());
      }
      myConnection.setOutput(null);
      myConnection.getListener().onOutput(myConnection, parentHeader.getMessageId());
    }
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    LOG.info("IPNB WebSocket was closed:  code " + code + " reason: " + reason);
  }

  @Override
  public void onError(Exception ex) {
    LOG.error(ex);
  }
}


