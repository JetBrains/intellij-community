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

import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.apache.xmlrpc.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/**
 * @author Dmitry Avdeev
 */
public class TracRepository extends BaseRepositoryImpl implements XmlRpcTransportFactory {

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    final XmlRpcClient client = getRpcClient();
    Object[] result = (Object[])client.execute("ticket.query", new Vector<Object>(Arrays.asList(query == null ? "" : query)));

    if (result == null) throw new Exception("Cannot connect to " + getUrl());

    List<Task> tasks = ContainerUtil.mapNotNull(result, new NullableFunction<Object, Task>() {
      @Override
      public Task fun(Object o) {
        try {
          return getTask((Integer)o, client);
        }
        catch (Exception e) {
          return null;
        }
      }
    });
    return tasks.toArray(new Task[tasks.size()]);
  }

  private XmlRpcClient getRpcClient() throws MalformedURLException {
    return new XmlRpcClient(new URL(getUrl()), this);
  }

  @Override
  public Task findTask(String id) throws Exception {
    return getTask(Integer.parseInt(id), getRpcClient());
  }

  private static Task getTask(int id, XmlRpcClient client) throws IOException, XmlRpcException {
    Object response = client.execute("ticket.get", new Vector(Arrays.asList(id)));
    LocalTaskImpl task = new LocalTaskImpl();
    return task;
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 0, 0);
  }

  @Override
  public BaseRepository clone() {
    return new TracRepository(this);
  }

  public TracRepository(TracRepositoryType repositoryType) {
    super(repositoryType);
  }

  private TracRepository(TracRepository other) {
    super(other);
  }

  @Override
  public XmlRpcTransport createTransport() throws XmlRpcClientException {
    try {
      return new CommonsXmlRpcTransport(new URL(getUrl()), getHttpClient());
    }
    catch (MalformedURLException e) {
      throw new XmlRpcClientException(e.getMessage(), e);
    }
  }

  @Override
  public void setProperty(String propertyName, Object value) {
  }
}
