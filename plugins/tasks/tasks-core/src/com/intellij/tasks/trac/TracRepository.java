// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.trac;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import icons.TasksCoreIcons;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Objects;
import java.util.Vector;
import javax.swing.Icon;
import org.apache.xmlrpc.CommonsXmlRpcTransport;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
@Tag("Trac")
@SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
public class TracRepository extends BaseRepositoryImpl {

  static {
    XmlRpc.setDefaultInputEncoding("UTF-8");
  }

  private String myDefaultSearch = "status!=closed&owner={username}&summary~={query}";
  private Boolean myMaxSupported;

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    Transport transport = new Transport();
    return getIssues(query, max, transport);
  }

  private Task[] getIssues(@Nullable String query, int max, final Transport transport) throws Exception {
    final XmlRpcClient client = getRpcClient();

    Vector<Object> result = null;
    String search = myDefaultSearch + "&max=" + max;
    if (myMaxSupported == null) {
      try {
        myMaxSupported = true;
        result = runQuery(query, transport, client, search);
      }
      catch (XmlRpcException e) {
        if (e.getMessage().contains("t.max")) {
          myMaxSupported = false;
        }
        else throw e;
      }
    }
    if (!myMaxSupported) {
      search = myDefaultSearch;
    }
    if (result == null) {
      result = runQuery(query, transport, client, search);
    }

    if (result == null) throw new Exception("Cannot connect to " + getUrl());

    ArrayList<Task> tasks = new ArrayList<>(max);
    int min = Math.min(max, result.size());
    for (int i = 0; i < min; i++) {
      Task task = getTask((Integer)result.get(i), client, transport);
      ContainerUtil.addIfNotNull(tasks, task);
    }
    return tasks.toArray(Task.EMPTY_ARRAY);
  }

  private Vector<Object> runQuery(@Nullable String query, Transport transport, XmlRpcClient client, String search)
    throws XmlRpcException, IOException {
    if (query != null) {
      search = search.replace("{query}", query);
    }
    search = search.replace("{username}", getUsername());
    XmlRpcRequest request = new XmlRpcRequest("ticket.query", new Vector<Object>(Collections.singletonList(search)));
    return (Vector<Object>)client.execute(request, transport);
  }

  private XmlRpcClient getRpcClient() throws MalformedURLException {
    return new XmlRpcClient(getUrl());
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    return getTask(Integer.parseInt(id), getRpcClient(), new Transport());
  }

  public String getDefaultSearch() {
    return myDefaultSearch;
  }

  public void setDefaultSearch(String defaultSearch) {
    myDefaultSearch = defaultSearch;
  }

  @Nullable
  private Task getTask(int id, XmlRpcClient client, Transport transport) throws IOException, XmlRpcException {
    XmlRpcRequest request = new XmlRpcRequest("ticket.get", new Vector(Collections.singletonList(id)));
    Object response = client.execute(request, transport);
    if (response == null) return null;
    final Vector<Object> vector = (Vector<Object>)response;
    final Hashtable<String, String> map = (Hashtable<String, String>)vector.get(3);
    return new Task() {

      @NotNull
      @Override
      public String getId() {
        return vector.get(0).toString();
      }

      @NotNull
      @Override
      public String getSummary() {
        return map.get("summary");
      }

      @Nullable
      @Override
      public String getDescription() {
        return null;
      }

      @Override
      public Comment @NotNull [] getComments() {
        return Comment.EMPTY_ARRAY;
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return TasksCoreIcons.Trac;
      }

      @NotNull
      @Override
      public TaskType getType() {
        TaskType taskType = TaskType.OTHER;
        String type = map.get("type");
        if (type == null) return taskType;
        if ("Feature".equals(type) || "enhancement".equals(type)) taskType = TaskType.FEATURE;
        else if ("Bug".equals(type) || "defect".equals(type) || "error".equals(type)) taskType = TaskType.BUG;
        else if ("Exception".equals(type)) taskType = TaskType.EXCEPTION;
        return taskType;
      }

      @Nullable
      @Override
      public Date getUpdated() {
        return getDate(vector.get(2));
      }

      @Nullable
      @Override
      public Date getCreated() {
        return getDate(vector.get(1));
      }

      @Override
      public boolean isClosed() {
        return false;
      }

      @Override
      public boolean isIssue() {
        return true;
      }

      @Nullable
      @Override
      public String getIssueUrl() {
        return null;
      }

      @Nullable
      @Override
      public TaskRepository getRepository() {
        return TracRepository.this;
      }
    };
  }

  private static Date getDate(Object o) {
    return o instanceof Date ? (Date)o : new Date((Integer)o * 1000L);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {

    return new CancellableConnection() {

      Transport myTransport;

      @Override
      protected void doTest() throws Exception {
        myTransport = new Transport();
        getIssues("", 1, myTransport);
      }

      @Override
      public void cancel() {
        myTransport.cancel();
      }
    };
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new TracRepository(this);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public TracRepository() {
    // for serialization
  }

  public TracRepository(TracRepositoryType repositoryType) {
    super(repositoryType);
    setUrl("http://myserver.com/login/rpc");
    myUseHttpAuthentication = true;
  }

  private TracRepository(TracRepository other) {
    super(other);
    myDefaultSearch = other.myDefaultSearch;
  }

  private class Transport extends CommonsXmlRpcTransport {
    Transport() throws MalformedURLException {
      super(new URL(getUrl()), getHttpClient());
    }

    void cancel() {
      method.abort();
    }
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && Objects.equals(((TracRepository)o).getDefaultSearch(), getDefaultSearch());
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | BASIC_HTTP_AUTHORIZATION;
  }
}
