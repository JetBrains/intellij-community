/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.intellij.openapi.project.Project;

/**
 * @author traff
 */
public class PydevRemoteConsoleCommunication extends PydevConsoleCommunication {
  /**
   * Initializes the xml-rpc communication.
   *
   * @param project
   * @param port       the port where the communication should happen.
   * @param process    this is the process that was spawned (server for the XML-RPC)
   * @param clientPort
   * @throws MalformedURLException
   */
  public PydevRemoteConsoleCommunication(Project project, int port, Process process, int clientPort)
    throws Exception {
    super(project, port, process, clientPort);
  }

  public PydevRemoteConsoleCommunication(Project project, int port, Process process, int clientPort, String clientHost) throws Exception {
    super(project, clientHost, port, process, clientPort);
  }
}
