/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.trac.TracRepository;
import org.apache.xmlrpc.CommonsXmlRpcTransport;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcRequest;

import java.net.URL;
import java.util.Arrays;
import java.util.Vector;

/**
 * @author Dmitry Avdeev
 *         Date: 1/25/12
 */
public abstract class TracIntegrationTest extends TaskManagerTestCase {

  public void testTracEncoding() throws Exception {

    XmlRpc.setDefaultInputEncoding("UTF-8");
    XmlRpcClient client = new XmlRpcClient("http://trac.shopware.de/trac/login/rpc");
 //   client.setBasicAuthentication();

    CommonsXmlRpcTransport transport = new CommonsXmlRpcTransport(new URL("http://trac.shopware.de/trac/login/rpc"));
    transport.setBasicAuthentication("jetbrains", "jetbrains");
    Object o = client.execute(new XmlRpcRequest("ticket.get", new Vector(Arrays.asList("5358"))),
            transport);

    System.out.println(o);

    TracRepository repository = new TracRepository();
    repository.setPassword("jetbrains");
    repository.setUsername("jetbrains");
    repository.setUrl("http://trac.shopware.de/trac/login/rpc");
    Task[] issues = repository.getIssues("", 10, 0);
    Task task = repository.findTask("5358");
    System.out.println(task.getDescription());
  }
}
