/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import org.apache.maven.execution.MavenExecutionRequest;

import java.io.File;
import java.rmi.RemoteException;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 1/20/2015
 */
public abstract class Maven3ServerEmbedder extends MavenRemoteObject implements MavenServerEmbedder {

  public final static boolean USE_MVN2_COMPATIBLE_DEPENDENCY_RESOLVING = System.getProperty("idea.maven3.use.compat.resolver") != null;

  @SuppressWarnings({"unchecked"})
  public abstract <T> T getComponent(Class<T> clazz, String roleHint);

  @SuppressWarnings({"unchecked"})
  public abstract <T> T getComponent(Class<T> clazz);

  public abstract void executeWithMavenSession(MavenExecutionRequest request, Runnable runnable);

  public abstract MavenExecutionRequest createRequest(File file,
                                                      List<String> activeProfiles,
                                                      List<String> inactiveProfiles,
                                                      List<String> goals)
    throws RemoteException;
}
