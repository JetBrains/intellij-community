package org.jetbrains.plugins.ipnb.protocol;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.intellij.openapi.util.text.StringUtil;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;

/**
 * @author vlan
 *
 * To be removed
*/
public class IpnbConnection {
  protected static final String API_URL = "/api";
  protected static final String KERNELS_URL = API_URL + "/kernels";
  protected static final String HTTP_POST = "POST";
  // TODO: Serialize cookies for the authentication message
  protected static final String authMessage = "{\"header\":{\"msg_id\":\"\", \"msg_type\":\"connect_request\"}, \"parent_header\":\"\", \"metadata\":{}," +
                                              "\"channel\":\"shell\" }";
  public static final String HTTP_DELETE = "DELETE";

  @NotNull protected final URI myURI;
  @NotNull protected final String myKernelId;
  @NotNull protected final String mySessionId;
  @NotNull protected final IpnbConnectionListener myListener;
  private WebSocketClient myShellClient;
  private WebSocketClient myIOPubClient;
  private Thread myShellThread;
  private Thread myIOPubThread;

  private volatile boolean myIsShellOpen = false;
  private volatile boolean myIsIOPubOpen = false;
  protected volatile boolean myIsOpened = false;

  private ArrayList<IpnbOutputCell> myOutput = new ArrayList<>();
  private int myExecCount;


  public IpnbConnection(@NotNull String uri, @NotNull IpnbConnectionListener listener) throws IOException, URISyntaxException {
    myURI = new URI(uri);
    myListener = listener;
    mySessionId = UUID.randomUUID().toString();
    myKernelId = startKernel();

    initializeClients();
  }

  protected void initializeClients() throws URISyntaxException {
    final Draft draft = new Draft17WithOrigin();

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
    myShellThread = new Thread(myShellClient, "IPNB shell client");
    myShellThread.start();

    myIOPubClient = new IpnbWebSocketClient(getIOPubURI(), draft);
    myIOPubThread = new Thread(myIOPubClient, "IPNB pub client");
    myIOPubThread.start();
  }

  protected void notifyOpen() {
    if (!myIsOpened && myIsShellOpen && myIsIOPubOpen) {
      myIsOpened = true;
      myListener.onOpen(this);
    }
  }

  public boolean isAlive() {
    return myShellClient.isOpen() && myIOPubClient.isOpen() ;
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

  protected void shutdownKernel() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId, HTTP_DELETE);
  }

  public void interrupt() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId + "/interrupt", HTTP_POST);
  }

  public void reload() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId + "/restart", HTTP_POST);
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
  protected String getWebSocketURIBase() {
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
    content.add("output_type", new JsonPrimitive(""));
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
  protected static class Header {
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
  protected static class Message {
    private Header header;
    private JsonObject parent_header;
    private JsonObject metadata;
    private JsonObject content;
    private JsonPrimitive channel;

    public static Message create(Header header, JsonObject parentHeader, JsonObject metadata, JsonObject content) {
      final Message message = new Message();
      message.header = header;
      message.parent_header = parentHeader;
      message.metadata = metadata;
      message.content = content;
      message.channel = new JsonPrimitive("shell");
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

  protected static void addCellOutput(@NotNull final PyContent content, ArrayList<IpnbOutputCell> output) {
    if (content instanceof PyErrContent) {
      output.add(new IpnbErrorOutputCell(((PyErrContent)content).getEvalue(),
                                         ((PyErrContent)content).getEname(), ((PyErrContent)content).getTraceback(), null, null));
    }
    else if (content instanceof PyStreamContent) {
      final String data = ((PyStreamContent)content).getData();
      output.add(new IpnbStreamOutputCell(((PyStreamContent)content).getName(), Lists.newArrayList(data), null, null));
    }
    else if (content instanceof PyOutContent) {
      final Map<String, Object> data = ((PyOutContent)content).getData();
      final String plainText = (String)data.get("text/plain");
      if (data.containsKey("text/latex")) {
        final String text = (String)data.get("text/latex");
        output.add(new IpnbLatexOutputCell(Lists.newArrayList(text), null, Lists.newArrayList(plainText), null));
      }
      else if (data.containsKey("text/html")) {
        final String html = (String)data.get("text/html");
        output.add(new IpnbHtmlOutputCell(Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(html)),
                                          Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(html)),
                                          ((PyOutContent)content).getExecutionCount(), null));
      }
      else if (data.containsKey("image/png")) {
        final String png = (String)data.get("image/png");
        output.add(new IpnbPngOutputCell(png, Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(plainText)), null, null));
      }
      else if (data.containsKey("image/jpeg")) {
        final String jpeg = (String)data.get("image/jpeg");
        output.add(new IpnbJpegOutputCell(jpeg, Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(plainText)), null, null));
      }
      else if (data.containsKey("image/svg")) {
        final String svg = (String)data.get("image/svg");
        output.add(new IpnbSvgOutputCell(Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(svg)),
                                         Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(plainText)), null, null));
      }
      else if (plainText != null){
        output.add(new IpnbOutOutputCell(Lists.newArrayList(plainText), ((PyOutContent)content).getExecutionCount(), null));
      }
    }
  }

  private interface PyContent {}

  @SuppressWarnings("UnusedDeclaration")
  protected static class Payload {
    String text;
    boolean replace;
    String source;
  }

  @SuppressWarnings("UnusedDeclaration")
  protected static class PyExecuteReplyContent implements PyContent {
    private int execution_count;
    private JsonObject metadata;
    private String status;
    private List<Payload> payload;

    public int getExecutionCount() {
      return execution_count;
    }

    public JsonObject getMetadata() {
      return metadata;
    }

    public List<Payload> getPayload() {
      return payload;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  protected static class PyOutContent implements PyContent {
    private int execution_count;
    private HashMap<String, Object> data;
    private JsonObject metadata;

    public int getExecutionCount() {
      return execution_count;
    }

    public Map<String, Object> getData() {
      return data;
    }

    public JsonObject getMetadata() {
      return metadata;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  protected static class PyErrContent implements PyContent {
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
  protected static class PyStreamContent implements PyContent {
    private String text;
    private String data;
    private String name;

    public String getData() {
      return data == null ? text : data;
    }

    public String getName() {
      return name;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  protected static class PyStatusContent {
    private String execution_state;

    public String getExecutionState() {
      return execution_state;
    }
  }

  protected class Draft17WithOrigin extends Draft_17 {
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

  protected class IpnbWebSocketClient extends WebSocketClient {
    protected IpnbWebSocketClient(@NotNull final URI serverUri, @NotNull final Draft draft) {
      super(serverUri, draft);
    }

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
      if ("pyout".equals(messageType) || "display_data".equals(messageType) || "execute_result".equals(messageType)) {
        final PyOutContent content = gson.fromJson(msg.getContent(), PyOutContent.class);
        addCellOutput(content, myOutput);
      }
      if ("execute_reply".equals(messageType)) {
        final PyExecuteReplyContent content = gson.fromJson(msg.getContent(), PyExecuteReplyContent.class);
        final List<Payload> payloads = content.payload;
        if (payloads != null && !payloads.isEmpty()) {
          final Payload payload = payloads.get(0);
          if (payload.replace) {
            myListener.onPayload(payload.text, parentHeader.getMessageId());
          }
        }
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
        final String executionState = content.getExecutionState();
        if ("idle".equals(executionState)) {
          myListener.onOutput(IpnbConnection.this, parentHeader.getMessageId());
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
  }

  public ArrayList<IpnbOutputCell> getOutput() {
    return myOutput;
  }

  public int getExecCount() {
    return myExecCount;
  }

}
