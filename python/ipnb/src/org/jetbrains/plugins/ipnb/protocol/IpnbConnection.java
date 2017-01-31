package org.jetbrains.plugins.ipnb.protocol;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.net.HTTPMethod;
import com.intellij.util.net.ssl.CertificateManager;
import org.apache.http.client.utils.URIBuilder;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ClientHandshakeBuilder;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.configuration.IpnbSettings;
import org.jetbrains.plugins.ipnb.format.cells.output.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author vlan
 *         <p>
 *         To be removed
 */
public class IpnbConnection {
  private static final Logger LOG = Logger.getInstance(IpnbConnection.class);
  protected static final String API_URL = "/api";
  private final static String DEFAULT_LOGIN_PATH = "/login";
  private static final String KERNEL_SPECS_PATH = "/kernelspecs";
  private static final String SESSIONS_PATH = "/sessions";
  private static final String USER_PATH = "/user";
  private static final String HUB_PREFIX = "/hub";
  private static final String SPAWN_URL = HUB_PREFIX + "/spawn";
  protected static final String KERNELS_URL = API_URL + "/kernels";
  
  private static final int ATTEMPT_TO_CONNECT_NUMBER = 10;

  // TODO: Serialize cookies for the authentication message
  protected static final String authMessage = "{\"header\":{\"msg_id\":\"\", \"msg_type\":\"connect_request\"}, \"parent_header\":\"\", \"metadata\":{}," +
                                              "\"channel\":\"shell\" }";
  public static final String AUTHENTICATION_NEEDED = "Authentication needed";

  @NotNull protected final URI myURI;
  @NotNull protected final String myKernelId;
  @NotNull protected final String mySessionId;
  @NotNull protected final IpnbConnectionListener myListener;
  @Nullable private final String myToken;
  private boolean myIsHubServer = false;
  private final Project myProject;
  private WebSocketClient myShellClient;
  private WebSocketClient myIOPubClient;
  private Thread myShellThread;
  private Thread myIOPubThread;

  private volatile boolean myIsShellOpen = false;
  private volatile boolean myIsIOPubOpen = false;
  protected volatile boolean myIsOpened = false;

  private IpnbOutputCell myOutput;
  private int myExecCount;
  private String myXsrf;
  private HashMap<String, String> myHeaders = new HashMap<>();
  private final CookieManager myCookieManager;

  public final static String UNABLE_LOGIN = "Unable to login: ";

  public IpnbConnection(@NotNull String uri, @NotNull IpnbConnectionListener listener,
                        @Nullable final String token, @NotNull Project project, @NotNull String pathToFile) throws IOException, URISyntaxException {
    myURI = new URI(uri);
    myListener = listener;
    myToken = token;
    mySessionId = UUID.randomUUID().toString();
    myProject = project;
    myCookieManager = new CookieManager();
    CookieHandler.setDefault(myCookieManager);
    initXSRF(myURI.toString());
    
    if (isRemote()) {
      String loginUrl = getLoginUrl();
      myIsHubServer = isHubServer(loginUrl);
      myKernelId = authorizeAndGetKernel(project, pathToFile, loginUrl);
    }
    else {
      if (myToken != null) {
        myHeaders.put("Authorization", "token " + myToken);
      }
      myKernelId = startKernel();
    }
    
    initializeClients();
  }

  private String authorizeAndGetKernel(@NotNull Project project, @NotNull String pathToFile, @NotNull String loginUrl) throws IOException {
    IpnbSettings ipnbSettings = IpnbSettings.getInstance(project);
    final String username = ipnbSettings.getUsername();
    String cookies = login(username, ipnbSettings.getPassword(), loginUrl);
    myHeaders.put("Cookie", cookies);
    if (myIsHubServer) {
      if (myXsrf == null) {
        initXSRF(myURI.toString() + "/user/" + username + "/tree?");
      }
      final Boolean started = startJupyterNotebookServer();
      if (!started) {
        throw new IOException("Cannot start Jupyter Notebook");
      }
    }
    final String kernelName = getDefaultKernelName();
    return getExistingKernelForSession(pathToFile, kernelName);
  }

  private boolean startJupyterNotebookServer() throws IOException {
    String serverStartUrl = getLocation(myURI + SPAWN_URL);

    if (serverStartUrl != null && serverStartUrl.startsWith(USER_PATH)) {
      for (int i = 0; i < ATTEMPT_TO_CONNECT_NUMBER; i++) {
        final String username = IpnbSettings.getInstance(myProject).getUsername();
        final String locationPrefix = USER_PATH + "/" + username + "/tree";
        final String location = getLocation(myURI + serverStartUrl);
        if (location != null && location.startsWith(locationPrefix)) {
          return true;
        }
        try {
          TimeUnit.MILLISECONDS.sleep(500);
        }
        catch (InterruptedException e) {
          LOG.warn(e.getMessage());
        }
      }
    }
    
    return false;
  }

  @Nullable
  private String getLocation(@NotNull String url) throws IOException {
    URLConnection urlConnection = new URL(url).openConnection();
    if (urlConnection instanceof HttpURLConnection) {
      final HttpURLConnection connection = configureConnection((HttpURLConnection)urlConnection, HTTPMethod.GET.name());
      try {
        return connection.getHeaderField("Location");
      }
      finally {
        connection.disconnect();
      }
    }
    return "";
  }

  private static boolean isHubServer(@NotNull String redirectUrl) {
    return redirectUrl.startsWith(HUB_PREFIX);
  }

  private void configureHttpsConnection() {
    HttpsURLConnection.setDefaultSSLSocketFactory(CertificateManager.getInstance().getSslContext().getSocketFactory());
    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
      @Override
      public boolean verify(String s, SSLSession session) {
        return myURI.getHost().equals(s);
      }
    });
  }

  private String login(@NotNull String username, @NotNull String password, @NotNull String loginUrl) throws IOException {
    String urlParameters = null;
    try {
      urlParameters = new URIBuilder()
        .addParameter("_xsrf=", myXsrf)
        .addParameter("username", username)
        .addParameter("password", password)
        .build().toString();
    }
    catch (URISyntaxException e) {
      LOG.warn(e.getMessage());
    }

    //String urlParameters = "_xsrf=" + myXsrf + "&" + "username=" + URLEncoder.encode(username, "UTF-8") + "&" + "password=" + 
    //                       URLEncoder.encode(password, "UTF-8");
    if (urlParameters == null) throw new IOException("Unable to login");
    byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
    final HttpsURLConnection connection = ObjectUtils.tryCast(configureConnection((HttpURLConnection)new URL(myURI + loginUrl).openConnection(),
                                                                             HTTPMethod.POST.name()), HttpsURLConnection.class);
    if (connection != null) {
      connection.setUseCaches(false);
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
      connection.setDoOutput(true);

      final OutputStream outputStream = connection.getOutputStream();
      try (DataOutputStream wr = new DataOutputStream(outputStream)) {
        wr.write(postData);
        wr.flush();
      }
      connection.connect();

      final int code = connection.getResponseCode();
      if (code != HttpURLConnection.HTTP_FORBIDDEN && code != HttpURLConnection.HTTP_UNAUTHORIZED) {
        final List<HttpCookie> cookies = myCookieManager.getCookieStore().getCookies();
        if (!cookies.isEmpty()) {
          return cookies.stream().map(cookie -> cookie.getName() + "=" + cookie.getValue()).collect(Collectors.joining(";"));
        }
      }
    }
    String message = connection == null ? "" : connection.getResponseCode() + " " + connection.getResponseMessage();
    throw new IOException(UNABLE_LOGIN + message);
  }

  private String getDefaultKernelName() {
    try {
      final String response = httpRequest(createApiUrl(KERNEL_SPECS_PATH), HTTPMethod.GET.name());
      final JsonObject kernelSpecs = ObjectUtils.tryCast(new JsonParser().parse(response), JsonObject.class);
      if (kernelSpecs != null && kernelSpecs.has("default")) {
        return kernelSpecs.get("default").getAsString();
      }
      else {
        LOG.warn("Got wrong kernel specs: " + response);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }

    return "";
  }

  @NotNull
  private String createApiUrl(@NotNull String path) {
    final String apiPrefix = myIsHubServer ? USER_PATH + "/" + IpnbSettings.getInstance(myProject).getUsername() : "";
    return myURI + apiPrefix + API_URL + path;
  }

  private String getExistingKernelForSession(@NotNull String pathToFile, @NotNull String kernelName) throws IOException {
    final byte[] postData = createNewFormatKernelPostParameters(pathToFile, kernelName);
    String wrapper = getKernelId(postData);
    if (wrapper != null) {
      return wrapper;
    }
    else {
      final byte[] oldParamsToPost = createOldFormatKernelPostParameters(pathToFile, kernelName);
      wrapper = getKernelId(oldParamsToPost);
      return wrapper;
    }
  }

  @Nullable
  private String getKernelId(byte[] postData) throws IOException {
    final URLConnection connection = new URL(createApiUrl(SESSIONS_PATH)).openConnection();
    if (connection instanceof HttpsURLConnection) {
      final HttpsURLConnection httpsConnection =
        ObjectUtils.tryCast(configureConnection((HttpURLConnection)connection, HTTPMethod.POST.name()),
                            HttpsURLConnection.class);
      if (httpsConnection != null) {
        httpsConnection.setRequestProperty("Content-Type", "application/json");
        httpsConnection.setRequestProperty("Content-Length", Integer.toString(postData.length));
        httpsConnection.setUseCaches(false);
        httpsConnection.setDoOutput(true);

        final OutputStream outputStream = connection.getOutputStream();
        try (DataOutputStream wr = new DataOutputStream(outputStream)) {
          wr.write(postData);
          wr.flush();
        }
        httpsConnection.connect();
        if (httpsConnection.getResponseCode() == HttpURLConnection.HTTP_CREATED) {
          final String response = getResponse(httpsConnection);
          final OldFormatSessionWrapper wrapper = new GsonBuilder().create().fromJson(response, OldFormatSessionWrapper.class);
          return wrapper.kernel.id;
        }
        httpsConnection.disconnect();
      }
    }
    return null;
  }

  private static byte[] createNewFormatKernelPostParameters(@NotNull String pathToFile, @NotNull String kernelName) {
    final SessionWrapper sessionWrapper = new SessionWrapper(kernelName, pathToFile, "notebook");
    return new GsonBuilder().create().toJson(sessionWrapper).getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] createOldFormatKernelPostParameters(@NotNull String pathToFile, @NotNull String kernelName) {
    final OldFormatSessionWrapper sessionWrapper = new OldFormatSessionWrapper(kernelName, pathToFile);
    return new GsonBuilder().create().toJson(sessionWrapper).getBytes(StandardCharsets.UTF_8);
  }

  private static boolean isLoginNeeded(@NotNull String redirectUrl) throws IOException {
    return redirectUrl.startsWith(DEFAULT_LOGIN_PATH) || redirectUrl.startsWith(HUB_PREFIX);
  }

  @NotNull
  private String getLoginUrl() throws IOException {
    String location = "";
    final String loginUrl = myURI.toString() + DEFAULT_LOGIN_PATH;
    final HttpsURLConnection connection = ObjectUtils.tryCast(new URL(loginUrl).openConnection(), HttpsURLConnection.class);
    if (connection != null) {
      configureHttpsConnection();
      connection.setInstanceFollowRedirects(false);
      connection.connect();
      if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
        location = connection.getHeaderField("Location");
        connection.disconnect();
      }
    }
    return location.isEmpty() ? DEFAULT_LOGIN_PATH : location;
  }

  private void initXSRF(String url) {
    URLConnection connection = null;
    try {
      connection = new URL(url).openConnection();
      connection.getHeaderFields();
      final List<HttpCookie> cookies = myCookieManager.getCookieStore().getCookies();
      for (HttpCookie cookie : cookies) {
        if ("_xsrf".equals(cookie.getName())) {
          myXsrf = cookie.getValue();
        }
      }
    }
    catch (IOException ignored) {
    }
    finally {
      if (connection instanceof HttpURLConnection) {
        ((HttpURLConnection)connection).disconnect();
      }
    }
  }

  protected void initializeClients() throws URISyntaxException {
    final Draft draft = new Draft17WithOrigin();

    myShellClient = new WebSocketClient(getShellURI(), draft, myHeaders, 0) {
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
    return myShellClient.isOpen() && myIOPubClient.isOpen();
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
    final String s = httpRequest(myURI + KERNELS_URL, HTTPMethod.POST.name());
    final Gson gson = new Gson();
    final Kernel kernel = gson.fromJson(s, Kernel.class);
    return kernel.getId();
  }

  protected void shutdownKernel() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId, HTTPMethod.DELETE.name());
  }

  public void interrupt() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId + "/interrupt", HTTPMethod.POST.name());
  }

  public void reload() throws IOException {
    httpRequest(myURI + KERNELS_URL + "/" + myKernelId + "/restart", HTTPMethod.POST.name());
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
    final String scheme = myURI.getScheme();
    String prefix = scheme.equals("http") ? "ws://" : "wss://";
    String hubPath = myIsHubServer ? USER_PATH + "/" + IpnbSettings.getInstance(myProject).getUsername() : "";
    return prefix + myURI.getAuthority() + hubPath + KERNELS_URL + "/" + myKernelId;
  }

  @NotNull
  private String httpRequest(@NotNull String url, @NotNull String method) throws IOException {
    final URLConnection urlConnection = new URL(url).openConnection();
    if (urlConnection instanceof HttpURLConnection) {
      final HttpURLConnection connection = configureConnection((HttpURLConnection)urlConnection, method);
      final int code = connection.getResponseCode();
      if (code == HttpURLConnection.HTTP_FORBIDDEN) {
        throw new IOException(AUTHENTICATION_NEEDED);
      }
      return getResponse(connection);
    }
    else {
      throw new UnsupportedOperationException("Only HTTP URLs are supported");
    }
  }

  @NotNull
  private static String getResponse(HttpURLConnection connection) throws IOException {
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
      connection.disconnect();
    }
  }

  @NotNull
  private HttpURLConnection configureConnection(HttpURLConnection urlConnection, @NotNull String method) throws ProtocolException {
    urlConnection.setRequestMethod(method);
    urlConnection.setReadTimeout(60000);
    urlConnection.setInstanceFollowRedirects(false);
    if (!StringUtil.isEmptyOrSpaces(myToken)) {
      urlConnection.setRequestProperty("Authorization", "token " + myToken);
    }
    else if (!StringUtil.isEmptyOrSpaces(myXsrf)) {
      urlConnection.setRequestProperty("X-XSRFToken", myXsrf);
    }
    if (!myHeaders.isEmpty()) {
      for (Map.Entry<String, String> entry : myHeaders.entrySet()) {
        urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
      }
    }
    return urlConnection;
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

  private boolean isRemote() {
    return !IpnbSettings.getInstance(myProject).getUsername().isEmpty() && !IpnbSettings.getInstance(myProject).getPassword().isEmpty();
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

  protected void addCellOutput(@NotNull final PyContent content) {
    if (content instanceof PyErrContent) {
      myOutput = new IpnbErrorOutputCell(((PyErrContent)content).getEvalue(),
                                         ((PyErrContent)content).getEname(), ((PyErrContent)content).getTraceback(), null, null);
    }
    else if (content instanceof PyStreamContent) {
      final String data = ((PyStreamContent)content).getData();
      myOutput = new IpnbStreamOutputCell(((PyStreamContent)content).getName(), Lists.newArrayList(data), null, null);
    }
    else if (content instanceof PyOutContent) {
      final Map<String, Object> data = ((PyOutContent)content).getData();
      final String plainText = (String)data.get("text/plain");
      if (data.containsKey("text/latex")) {
        final String text = (String)data.get("text/latex");
        myOutput = new IpnbLatexOutputCell(Lists.newArrayList(text), false, null, Lists.newArrayList(plainText), null);
      }
      else if (data.containsKey("text/markdown")) {
        final String text = (String)data.get("text/markdown");
        myOutput = new IpnbLatexOutputCell(Lists.newArrayList(text), true, null, Lists.newArrayList(plainText), null);
      }
      else if (data.containsKey("text/html")) {
        final String html = (String)data.get("text/html");
        myOutput = new IpnbHtmlOutputCell(Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(html)),
                                          Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(html)),
                                          ((PyOutContent)content).getExecutionCount(), null);
      }
      else if (data.containsKey("image/png")) {
        final String png = (String)data.get("image/png");
        myOutput = new IpnbPngOutputCell(png, Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(plainText)), null, null);
      }
      else if (data.containsKey("image/jpeg")) {
        final String jpeg = (String)data.get("image/jpeg");
        myOutput = new IpnbJpegOutputCell(jpeg, Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(plainText)), null, null);
      }
      else if (data.containsKey("image/svg")) {
        final String svg = (String)data.get("image/svg");
        myOutput = new IpnbSvgOutputCell(Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(svg)),
                                         Lists.newArrayList(StringUtil.splitByLinesKeepSeparators(plainText)), null, null);
      }
      else if (plainText != null) {
        myOutput = new IpnbOutOutputCell(Lists.newArrayList(plainText), ((PyOutContent)content).getExecutionCount(), null);
      }
    }
  }

  private interface PyContent {
  }

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
      super(serverUri, draft, myHeaders, 10000);
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
        addCellOutput(content);
        myListener.onOutput(IpnbConnection.this, parentHeader.getMessageId());
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
        if ("ok".equals(content.status) || "error".equals(content.status)) {
          myListener.onFinished(IpnbConnection.this, parentHeader.getMessageId());
        }
      }
      else if ("pyerr".equals(messageType) || "error".equals(messageType)) {
        final PyErrContent content = gson.fromJson(msg.getContent(), PyErrContent.class);
        addCellOutput(content);
        myListener.onOutput(IpnbConnection.this, parentHeader.getMessageId());
      }
      else if ("stream".equals(messageType)) {
        final PyStreamContent content = gson.fromJson(msg.getContent(), PyStreamContent.class);
        addCellOutput(content);
        myListener.onOutput(IpnbConnection.this, parentHeader.getMessageId());
      }
      else if ("pyin".equals(messageType) || "execute_input".equals(messageType)) {
        final JsonElement executionCount = msg.getContent().get("execution_count");
        if (executionCount != null) {
          myExecCount = executionCount.getAsInt();
        }
        myOutput = null;
        myListener.onOutput(IpnbConnection.this, parentHeader.getMessageId());
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

  public IpnbOutputCell getOutput() {
    return myOutput;
  }

  public int getExecCount() {
    return myExecCount;
  }
  
  private static class SessionWrapper {
    KernelWrapper kernel;
    String name;
    String path;
    String type = "notebook";

    public SessionWrapper(String kernelName, String path, String type) {
      this.kernel = new KernelWrapper(kernelName);
      this.name = "";
      this.path = path;
      this.type = type;
    }
  }

  private static class OldFormatSessionWrapper {
    NotebookWrapper notebook;
    KernelWrapper kernel;

    public OldFormatSessionWrapper(String interpreterName, String filePath) {
      kernel = new KernelWrapper(interpreterName);
      notebook = new NotebookWrapper(filePath);
    }
  }

  private static class KernelWrapper {
    String id;
    String name;

    public KernelWrapper(String name) {
      this.name = name;
    }
  }

  private static class NotebookWrapper {
    String path;

    public NotebookWrapper(String path) {
      this.path = path;
    }
  }
}
