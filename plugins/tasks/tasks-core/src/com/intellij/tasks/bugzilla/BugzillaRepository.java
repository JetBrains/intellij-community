package com.intellij.tasks.bugzilla;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.xmlrpc.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
@SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
@Tag("Bugzilla")
public class BugzillaRepository extends BaseRepositoryImpl {

  private static final Logger LOG = Logger.getInstance(BugzillaRepository.class);

  // Copied from SendTimeTrackingInformationDialog
  public static final Pattern TIME_SPENT_PATTERN = Pattern.compile("([0-9]+)d ([0-9]+)h ([0-9]+)m");

  private Version myVersion;

  private boolean myAuthenticated;
  private String myAuthenticationToken;

  private String myProductName = "";
  private String myComponentName = "";

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public BugzillaRepository() {
    // empty
  }

  /**
   * Normal instantiation constructor
   */
  public BugzillaRepository(TaskRepositoryType type) {
    super(type);
    setUseHttpAuthentication(false);
    setUrl("http://myserver.com/xmlrpc.cgi");
  }

  /**
   * Cloning constructor
   */
  public BugzillaRepository(BugzillaRepository other) {
    super(other);
    myProductName = other.myProductName;
    myComponentName = other.myComponentName;
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new BugzillaRepository(this);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed, @NotNull ProgressIndicator cancelled)
    throws Exception {
    // Method search appeared in Bugzilla 3.4, ensureVersionDiscovered() checks minimal
    // supported version requirement.
    Hashtable<String, Object> response = createIssueSearchRequest(query, offset, limit, withClosed).execute();

    Vector<Hashtable<String, Object>> bugs = (Vector<Hashtable<String, Object>>)response.get("bugs");
    return ContainerUtil.map2Array(bugs, BugzillaTask.class, new Function<Hashtable<String, Object>, BugzillaTask>() {
      @Override
      public BugzillaTask fun(Hashtable<String, Object> hashTable) {
        return new BugzillaTask(hashTable, BugzillaRepository.this);
      }
    });
  }

  private BugzillaXmlRpcRequest createIssueSearchRequest(String query, int offset, int limit, boolean withClosed) throws Exception {
    // Method search appeared in Bugzilla 3.4, ensureVersionDiscovered() checks minimal
    // supported version requirement.
    return new BugzillaXmlRpcRequest("Bug.search")
      .requireAuthentication(true)
      .withParameter("summary", StringUtil.isNotEmpty(query) ? newVector(query.split("\\s+")) : null)
      .withParameter("product", StringUtil.nullize(myProductName))
        // Bugzilla's API allows to specify component even without parental project
      .withParameter("component", StringUtil.nullize(myComponentName))
      .withParameter("offset", offset)
      .withParameter("limit", limit)
      .withParameter("assigned_to", getUsername())
      .withParameter("resolution", !withClosed ? "" : null);
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    Hashtable<String, Object> response;
    try {
      // In Bugzilla 3.0 this method is called "get_bugs".
      response = new BugzillaXmlRpcRequest("Bug.get").requireAuthentication(true).withParameter("ids", newVector(id)).execute();
    }
    catch (XmlRpcException e) {
      if (e.code == 101 && e.getMessage().contains("does not exist")) {
        return null;
      }
      throw e;
    }
    Vector<Hashtable<String, Object>> bugs = (Vector<Hashtable<String, Object>>)response.get("bugs");
    if (bugs == null || bugs.isEmpty()) {
      return null;
    }
    return new BugzillaTask(bugs.get(0), this);
  }

  private void ensureVersionDiscovered() throws Exception {
    if (myVersion == null) {
      Hashtable<String, Object> result = new BugzillaXmlRpcRequest("Bugzilla.version").execute();
      if (result == null) {
        throw new RequestFailedException(TaskBundle.message("bugzilla.failure.no.version"));
      }
      String version = (String)result.get("version");
      String[] parts = version.split("\\.", 3);
      myVersion = new Version(Integer.parseInt(parts[0]), 
                              parts.length > 1 ? Integer.parseInt(parts[1]) : 0, 
                              parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
      if (myVersion.lessThan(3, 4)) {
        throw new RequestFailedException("Bugzilla before 3.4 is not supported");
      }
    }
  }

  private void ensureUserAuthenticated() throws Exception {
    ensureVersionDiscovered();
    if (!myAuthenticated) {
      Hashtable<String, Object> response = new BugzillaXmlRpcRequest("User.login")
        .withParameter("login", getUsername())
        .withParameter("password", getPassword())
        .execute();
      myAuthenticated = true;
      // Not available in Bugzilla before 4.4
      myAuthenticationToken = (String)response.get("token");
    }
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull TaskState state) throws Exception {
    BugzillaXmlRpcRequest request = new BugzillaXmlRpcRequest("Bug.update")
      .requireAuthentication(true)
      .withParameter("ids", newVector(task.getId()));

    switch (state) {
      case IN_PROGRESS:
        request.withParameter("status", "IN_PROGRESS");
        break;
      case RESOLVED:
        request.withParameter("status", "RESOLVED").withParameter("resolution", "FIXED");
        break;
      default:
        return;
    }

    request.execute();
  }

  @Override
  public void updateTimeSpent(@NotNull LocalTask task, @NotNull String timeSpent, @NotNull String comment) throws Exception {
    LOG.debug(String.format("Last post: %s, time spent from last: %s, time spent: %s",
                            task.getLastPost(), task.getTimeSpentFromLastPost(), timeSpent));
    Matcher matcher = TIME_SPENT_PATTERN.matcher(timeSpent);
    if (matcher.find()) {
      int days = Integer.valueOf(matcher.group(1));
      int hours = Integer.valueOf(matcher.group(2));
      int minutes = Integer.valueOf(matcher.group(3));
      BugzillaXmlRpcRequest request = new BugzillaXmlRpcRequest("Bug.update")
        .requireAuthentication(true)
        .withParameter("ids", newVector(task.getId()))
        // the number of hours worked on the bug as double
        .withParameter("work_time", days * 24 + hours + minutes / 60.0);
      if (!StringUtil.isEmptyOrSpaces(comment)) {
        request.withParameter("comment", newHashTable("body", comment, "is_private", false));
      }
      request.execute();
    } else {
      LOG.error("Illegal time spent format: " + timeSpent);
    }
  }

  /**
   * @return pair where first element is list of project names and second is list of component names (will be empty for Bugzilla < 4.2)
   */
  @NotNull
  public Pair<List<String>, List<String>> fetchProductAndComponentNames() throws Exception {
    Hashtable<String, Vector<Integer>> productIdsResponse = new BugzillaXmlRpcRequest("Product.get_selectable_products")
      .requireAuthentication(true)
      .execute();

    Hashtable<String, Object> productInfoResponse = new BugzillaXmlRpcRequest("Product.get")
      .requireAuthentication(true)
      .withParameter("ids", productIdsResponse.get("ids"))
      .execute();

    List<String> productNames = new ArrayList<String>();
    List<String> componentNames = new ArrayList<String>();

    for (Hashtable<String, Object> info : (Vector<Hashtable<String, Object>>)productInfoResponse.get("products")) {
      productNames.add((String)info.get("name"));
      if (myVersion != null && myVersion.isOrGreaterThan(4, 2)) {
        for (Hashtable<String, Object> component : (Vector<Hashtable<String, Object>>)info.get("components")) {
          componentNames.add((String)component.get("name"));
        }
      }
    }
    return Pair.create(productNames, componentNames);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {

      BugzillaXmlRpcRequest myRequest;

      @Override
      protected void doTest() throws Exception {
        // Reset information about server.
        myVersion = null;
        myAuthenticated = false;
        myAuthenticationToken = null;

        myRequest = createIssueSearchRequest(null, 0, 1, true);
        myRequest.execute();
      }

      @Override
      public void cancel() {
        myRequest.cancel();
      }
    };
  }

  private static <T> Vector<T> newVector(T... elements) {
    return new Vector<T>(Arrays.asList(elements));
  }

  private static <K, V> Hashtable<K, V> newHashTable(Object... pairs) {
    assert pairs.length % 2 == 0;
    Hashtable<K, V> table = new Hashtable<K, V>();
    for (int i = 0; i < pairs.length; i += 2) {
      // Null values are not allowed, because Bugzilla reacts unexpectedly on them.
      if (pairs[i + 1] != null) {
        table.put((K)pairs[i], (V)pairs[i + 1]);
      }
    }
    return table;
  }

  @Nullable
  @Override
  public String extractId(@NotNull String taskName) {
    String id = taskName.trim();
    return id.matches("\\d+") ? id : null;
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && StringUtil.isNotEmpty(getUsername()) && StringUtil.isNotEmpty(getPassword());
  }

  @Override
  protected int getFeatures() {
    int features = super.getFeatures();
    // Status and work time updates are available through Bug.update method which is available only in Bugzilla 4.0+
    if (myVersion != null && myVersion.isOrGreaterThan(4, 0)) {
      return features | STATE_UPDATING | TIME_MANAGEMENT;
    }
    return features;
  }

  private class BugzillaXmlRpcRequest {
    // Copied from Trac repository
    private class Transport extends CommonsXmlRpcTransport {
      public Transport() throws MalformedURLException {
        super(new URL(getUrl()), getHttpClient());
      }

      public void cancel() {
        method.abort();
      }
    }

    private final String myMethodName;
    private boolean myRequireAuthentication;
    private final HashMap<String, Object> myParameters = new HashMap<String, Object>();
    private final Transport myTransport;

    public BugzillaXmlRpcRequest(@NotNull String methodName) throws MalformedURLException {
      myMethodName = methodName;
      myTransport = new Transport();
    }

    public BugzillaXmlRpcRequest withParameter(@NotNull String name, @Nullable Object value) {
      if (value != null) {
        myParameters.put(name, value);
      }
      return this;
    }

    public BugzillaXmlRpcRequest requireAuthentication(boolean require) {
      myRequireAuthentication = require;
      return this;
    }

    public void cancel() {
      myTransport.cancel();
    }

    public <T> T execute() throws Exception {
      if (myRequireAuthentication) {
        ensureUserAuthenticated();
        // Bugzilla [3.0, 4.4) uses cookies authentication.
        // Bugzilla [3.6, ...) allows to send login ("Bugzilla_login") and password ("Bugzilla_password")
        // with every requests for automatic authentication (not used here).
        // Bugzilla [4.4, ...) also allows to send token ("Bugzilla_token") returned by call to User.login
        // with any request to its API.
        if (myVersion.isOrGreaterThan(4, 4) && myAuthenticationToken != null) {
          myParameters.put("Bugzilla_token", myAuthenticationToken);
        }
      }
      Vector<Hashtable<String, Object>> parameters = new Vector<Hashtable<String, Object>>();
      parameters.add(new Hashtable<String, Object>(myParameters));
      try {
        return  (T)new XmlRpcClient(getUrl()).execute(new XmlRpcRequest(myMethodName, parameters), myTransport);
      }
      catch (XmlRpcClientException e) {
        // Unfortunately there is no standard error code to identify this kind of error in portable way
        if (e.getMessage().equals("Error decoding XML-RPC response")) {
          throw new RequestFailedException(TaskBundle.message("bugzilla.failure.malformed.response"), e);
        }
        throw e;
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    if (!(o instanceof BugzillaRepository)) return false;
    BugzillaRepository repository = (BugzillaRepository)o;

    if (!Comparing.equal(myProductName, repository.getProductName())) return false;
    if (!Comparing.equal(myComponentName, repository.getComponentName())) return false;

    return true;
  }

  @NotNull
  public String getProductName() {
    return myProductName;
  }

  public void setProductName(@NotNull String productName) {
    myProductName = productName;
  }

  @NotNull
  public String getComponentName() {
    return myComponentName;
  }

  public void setComponentName(@NotNull String componentName) {
    myComponentName = componentName;
  }
}
