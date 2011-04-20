/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.tasks.trac;

import com.intellij.openapi.util.Comparing;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.xmlrpc.CommonsXmlRpcTransport;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@Tag("Trac")
@SuppressWarnings({"UseOfObsoleteCollectionType", "unchecked"})
public class TracRepository extends BaseRepositoryImpl {

  private String myDefaultSearch = "status!=closed&owner={username}&summary~={query}";

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    Transport transport = new Transport();
    return getIssues(query, max, transport);
  }

  private Task[] getIssues(String query, int max, final Transport transport) throws Exception {
    final XmlRpcClient client = getRpcClient();
    String search = myDefaultSearch + "&max=" + max;
    if (query != null) {
      search = search.replace("{query}", query);
    }
    search = search.replace("{username}", getUsername());
    XmlRpcRequest request = new XmlRpcRequest("ticket.query", new Vector<Object>(Arrays.asList(search)));
    Vector<Object> result = (Vector<Object>)client.execute(request, transport);

    if (result == null) throw new Exception("Cannot connect to " + getUrl());

    List<Task> tasks = ContainerUtil.mapNotNull(result, new NullableFunction<Object, Task>() {
      @Override
      public Task fun(Object o) {
        try {
          return getTask((Integer)o, client, transport);
        }
        catch (Exception e) {
          return null;
        }
      }
    });
    return tasks.toArray(new Task[tasks.size()]);
  }

  private XmlRpcClient getRpcClient() throws MalformedURLException {
    return new XmlRpcClient(new URL(getUrl()));
  }

  @Override
  public Task findTask(String id) throws Exception {
    return getTask(Integer.parseInt(id), getRpcClient(), new Transport());
  }

  public String getDefaultSearch() {
    return myDefaultSearch;
  }

  public void setDefaultSearch(String defaultSearch) {
    myDefaultSearch = defaultSearch;
  }

  @Nullable
  private static Task getTask(int id, XmlRpcClient client, Transport transport) throws IOException, XmlRpcException {
    XmlRpcRequest request = new XmlRpcRequest("ticket.get", new Vector(Arrays.asList(id)));
    Object response = client.execute(request, transport);
    if (response == null) return null;
    Vector<Object> vector = (Vector<Object>)response;
    LocalTaskImpl task = new LocalTaskImpl();
    task.setId(vector.get(0).toString());
    task.setCreated((Date)vector.get(1));
    task.setUpdated((Date)vector.get(2));

    Hashtable<String, String> map = (Hashtable<String, String>)vector.get(3);
    task.setSummary(map.get("summary"));

    TaskType taskType = TaskType.OTHER;
    String type = map.get("type");
    if ("Feature".equals(type)) taskType = TaskType.FEATURE;
    else if ("Bug".equals(type)) taskType = TaskType.BUG;
    else if ("Exception".equals(type)) taskType = TaskType.EXCEPTION;
    task.setType(taskType);

    task.setIssue(true);
    return task;
  }

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
    public Transport() throws MalformedURLException {
      super(new URL(getUrl()), getHttpClient());
    }

    void cancel() {
      method.abort();
    }
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  @Override
  public boolean equals(Object o) {
    return super.equals(o) && Comparing.equal(((TracRepository)o).getDefaultSearch(), getDefaultSearch());
  }
}
