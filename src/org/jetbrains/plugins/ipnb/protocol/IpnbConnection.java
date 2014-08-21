package org.jetbrains.plugins.ipnb.protocol;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;

/**
* @author vlan
*/
public class IpnbConnection {
  private static final String API_URL = "/api";
  private static final String KERNELS_URL = API_URL + "/kernels";
  private static final String HTTP_POST = "POST";
  public static final String HTTP_DELETE = "DELETE";

  @NotNull private final URI myURI;
  @NotNull private final String myKernelId;
  @NotNull private final String mySessionId;
  @NotNull private final IpnbConnectionListener myListener;
  @NotNull private final WebSocketClient myShellClient;
  @NotNull private final WebSocketClient myIOPubClient;
  @NotNull private final Thread myShellThread;
  @NotNull private final Thread myIOPubThread;

  private volatile boolean myIsShellOpen = false;
  private volatile boolean myIsIOPubOpen = false;
  private volatile boolean myIsOpened = false;

  public IpnbConnection(@NotNull URI uri, @NotNull IpnbConnectionListener listener) throws IOException, URISyntaxException {
    myURI = uri;
    myListener = listener;
    mySessionId = UUID.randomUUID().toString();
    myKernelId = startKernel();

    final Draft draft = new Draft17WithOrigin();
    // TODO: Serialize cookies for the authentication message
    final String authMessage = "identity:foo";

    myShellClient = new WebSocketClient(getShellURI(), draft) {
      @Override
      public void onOpen(@NotNull ServerHandshake handshakeData) {
        send(authMessage);
        myIsShellOpen = true;
        notifyOpen();
      }

      @Override
      public void onMessage(@NotNull String message) {
      }

      @Override
      public void onClose(int code, @NotNull String reason, boolean remote) {
      }

      @Override
      public void onError(@NotNull Exception e) {
      }
    };
    myShellThread = new Thread(myShellClient);
    myShellThread.start();

    myIOPubClient = new WebSocketClient(getIOPubURI(), draft) {
      private LinkedHashMap<String, String> myOutput = new LinkedHashMap<String, String>();

      @Override
      public void onOpen(ServerHandshake handshakeData) {
        send(authMessage);
        myIsIOPubOpen = true;
        notifyOpen();
      }

      @Override
      public void onMessage(String message) {
        final Gson gson = new Gson();
        final Message msg = gson.fromJson(message, Message.class);
        final Header header = msg.getHeader();
        final Header parentHeader = gson.fromJson(msg.getParentHeader(), Header.class);
        final String messageType = header.getMessageType();
        if ("pyout".equals(messageType)) {
          final PyOutContent content = gson.fromJson(msg.getContent(), PyOutContent.class);
          myOutput.putAll(content.getData());
        }
        else if ("pyerr".equals(messageType)) {
          final PyErrContent content = gson.fromJson(msg.getContent(), PyErrContent.class);
          myOutput.putAll(ImmutableMap.of("error", content.getEvalue()));
        }
        else if ("stream".equals(messageType)) {
          final PyStreamContent content = gson.fromJson(msg.getContent(), PyStreamContent.class);
          myOutput.putAll(ImmutableMap.of("stream", content.getData()));
        }
        else if ("status".equals(messageType)) {
          final PyStatusContent content = gson.fromJson(msg.getContent(), PyStatusContent.class);
          if (content.getExecutionState().equals("idle")) {
            //noinspection unchecked
            myListener.onOutput(IpnbConnection.this, parentHeader.getMessageId(), (Map<String, String>)myOutput.clone());
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
    myIOPubThread = new Thread(myIOPubClient);
    myIOPubThread.start();
  }

  private void notifyOpen() {
    if (!myIsOpened && myIsShellOpen && myIsIOPubOpen) {
      myIsOpened = true;
      myListener.onOpen(this);
    }
  }

  @NotNull
  public String execute(@NotNull String code) {
    final String messageId = UUID.randomUUID().toString();
    myShellClient.send(new Gson().toJson(createExecuteRequest(code, messageId)));
    return messageId;
  }

  public void shutdown() {
    myIOPubClient.close();
    myShellClient.close();
  }

  public void close() throws IOException, InterruptedException {
    myIOPubThread.join();
    myShellThread.join();
    shutdownKernel();
  }

  @NotNull
  public String getKernelId() {
    return myKernelId;
  }

  @NotNull
  private String startKernel() throws IOException {
    final String s = httpRequest(myURI + KERNELS_URL, HTTP_POST);
    final Gson gson = new Gson();
    final Kernel kernel = gson.fromJson(s, Kernel.class);
    return kernel.getId();
  }

  private void shutdownKernel() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId, HTTP_DELETE);
  }

  @NotNull
  public URI getShellURI() throws URISyntaxException {
    return new URI(getWebSocketURIBase() + "/shell");
  }

  @NotNull
  public URI getIOPubURI() throws URISyntaxException {
    return new URI(getWebSocketURIBase() + "/iopub");
  }

  @NotNull
  private String getWebSocketURIBase() {
    return "ws://" + myURI.getAuthority() + KERNELS_URL + "/" + myKernelId;
  }

  @NotNull
  private static String httpRequest(@NotNull String url, @NotNull String method) throws IOException {
    final URLConnection urlConnection = new URL(url).openConnection();
    if (urlConnection instanceof HttpURLConnection) {
      final HttpURLConnection connection = (HttpURLConnection)urlConnection;
      connection.setRequestMethod(method);
      final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
      try {
        final StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) != -1) {
          builder.append(buffer, 0, n);
        }
        return builder.toString();
      }
      finally {
        reader.close();
      }
    }
    else {
      throw new UnsupportedOperationException("Only HTTP URLs are supported");
    }
  }

  @NotNull
  public Message createExecuteRequest(String code, String messageId) {
    final JsonObject content = new JsonObject();
    content.addProperty("code", code);
    content.addProperty("silent", false);
    content.add("user_variables", new JsonArray());
    content.add("user_expressions", new JsonObject());
    content.addProperty("allow_stdin", false);

    return createMessage("execute_request", content, messageId);
  }

  private Message createMessage(String messageType, JsonObject content, String messageId) {
    final Header header = Header.create(messageId, "username", mySessionId, messageType);
    final JsonObject parentHeader = new JsonObject();

    final JsonObject metadata = new JsonObject();

    return Message.create(header, parentHeader, metadata, content);
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Kernel {
    @NotNull private String id;

    @NotNull
    public String getId() {
      return id;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Header {
    private String msg_id;
    private String username;
    private String session;
    private String msg_type;

    @NotNull
    public static Header create(String messageId, String username, String sessionId, String messageType) {
      final Header header = new Header();
      header.msg_id = messageId;
      header.username = username;
      header.session = sessionId;
      header.msg_type = messageType;
      return header;
    }

    public String getMessageId() {
      return msg_id;
    }

    public String getUsername() {
      return username;
    }

    public String getSessionId() {
      return session;
    }

    public String getMessageType() {
      return msg_type;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Message {
    private Header header;
    private JsonObject parent_header;
    private JsonObject metadata;
    private JsonObject content;

    public static Message create(Header header, JsonObject parentHeader, JsonObject metadata, JsonObject content) {
      final Message message = new Message();
      message.header = header;
      message.parent_header = parentHeader;
      message.metadata = metadata;
      message.content = content;
      return message;
    }

    public Header getHeader() {
      return header;
    }

    public JsonObject getParentHeader() {
      return parent_header;
    }

    public JsonObject getMetadata() {
      return metadata;
    }

    public JsonObject getContent() {
      return content;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class PyOutContent {
    private int execution_count;
    private HashMap<String, String> data;
    private JsonObject metadata;

    public int getExecutionCount() {
      return execution_count;
    }

    public Map<String, String> getData() {
      return data;
    }

    public JsonObject getMetadata() {
      return metadata;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class PyErrContent {
    private String ename;
    private String evalue;
    private List<String> traceback;

    public String getEname() {
      return ename;
    }

    public String getEvalue() {
      return evalue;
    }

    public List<String> getTraceback() {
      return traceback;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class PyStreamContent {
    private String data;
    private String name;

    public String getData() {
      return data;
    }

    public String getName() {
      return name;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class PyStatusContent {
    private String execution_state;

    public String getExecutionState() {
      return execution_state;
    }
  }

  private class Draft17WithOrigin extends Draft_17 {
    @Override
    public Draft copyInstance() {
      return new Draft17WithOrigin();
    }

    @NotNull
    @Override
    public ClientHandshakeBuilder postProcessHandshakeRequestAsClient(@NotNull ClientHandshakeBuilder request) {
      super.postProcessHandshakeRequestAsClient(request);
      request.put("Origin", myURI.toString());
      return request;
    }
  }
}
