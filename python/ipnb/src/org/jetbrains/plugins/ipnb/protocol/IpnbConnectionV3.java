package org.jetbrains.plugins.ipnb.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbOutputCell;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IpnbConnectionV3 extends IpnbConnection{
  private WebSocketClient myChannelsClient;
  private Thread myChannelsThread;

  public IpnbConnectionV3(@NotNull URI uri, @NotNull IpnbConnectionListener listener) throws IOException, URISyntaxException {
    super(uri, listener);
  }

  @Override
  protected void initializeClients() throws URISyntaxException {
    final Draft draft = new Draft17WithOrigin();

    myChannelsClient = new WebSocketClient(getChannelsURI(), draft) {
      private ArrayList<IpnbOutputCell> myOutput = new ArrayList<IpnbOutputCell>();
      private Integer myExecCount = null;
      @Override
      public void onOpen(ServerHandshake handshakeData) {
        send(authMessage);
        notifyOpen();
      }

      @Override
      public void onMessage(String message) {
        final Gson gson = new Gson();
        final Message msg = gson.fromJson(message, Message.class);
        final Header header = msg.getHeader();
        final Header parentHeader = gson.fromJson(msg.getParentHeader(), Header.class);
        final String messageType = header.getMessageType();
        if ("pyout".equals(messageType) || "display_data".equals(messageType)) {
          final PyOutContent content = gson.fromJson(msg.getContent(), PyOutContent.class);
          addCellOutput(content, myOutput);
        }
        else if ("pyerr".equals(messageType) || "error".equals(messageType)) {
          final PyErrContent content = gson.fromJson(msg.getContent(), PyErrContent.class);
          addCellOutput(content, myOutput);
        }
        else if ("stream".equals(messageType)) {
          final PyStreamContent content = gson.fromJson(msg.getContent(), PyStreamContent.class);
          addCellOutput(content, myOutput);
        }
        else if ("pyin".equals(messageType) || "execute_input".equals(messageType)) {
          final JsonElement executionCount = msg.getContent().get("execution_count");
          if (executionCount != null) {
            myExecCount = executionCount.getAsInt();
          }
        }
        else if ("status".equals(messageType)) {
          final PyStatusContent content = gson.fromJson(msg.getContent(), PyStatusContent.class);
          if (content.getExecutionState().equals("idle")) {
            //noinspection unchecked
            myListener.onOutput(IpnbConnectionV3.this, parentHeader.getMessageId(), (List<IpnbOutputCell>)myOutput.clone(), myExecCount);
            myOutput.clear();
          }
        }
      }

      @Override
      public void onClose(int code, String reason, boolean remote) {

      }

      @Override
      public void onError(Exception ex) {

      }
    };
    myChannelsThread = new Thread(myChannelsClient);
    myChannelsThread.start();
  }

  protected void notifyOpen() {
    if (!myIsOpened) {
      myIsOpened = true;
      myListener.onOpen(this);
    }
  }

  @NotNull
  public String execute(@NotNull String code) {
    final String messageId = UUID.randomUUID().toString();
    myChannelsClient.send(new Gson().toJson(createExecuteRequest(code, messageId)));
    return messageId;
  }

  public void shutdown() {
    myChannelsClient.close();
  }

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
